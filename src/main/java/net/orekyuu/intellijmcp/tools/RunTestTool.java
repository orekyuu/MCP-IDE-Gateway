package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RunTestTool extends AbstractMcpTool<Object> {

    private static final Logger LOG = Logger.getInstance(RunTestTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_STACKTRACE_LINES = 100;

    private static final Arg<ProjectRelativePath> FILE_PATH =
            Arg.projectRelativePath("filePath", "Relative path to the test file from the project root");
    private static final Arg<Optional<String>> METHOD_NAME =
            Arg.string("methodName", "Specific test method/function name to run. If omitted, runs all tests in the file").optional();
    private static final Arg<Optional<String>> CONFIGURATION_NAME =
            Arg.string("configurationName", "Name of the run configuration to use. Required when multiple configurations are available. Call without this parameter first to get the list of available configurations.").optional();
    private static final Arg<Integer> TIMEOUT_SECONDS =
            Arg.integer("timeoutSeconds", "Timeout in seconds for test execution").min(0).optional(DEFAULT_TIMEOUT_SECONDS);
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getName() {
        return "run_test";
    }

    @Override
    public String getDescription() {
        return "Run tests in a file or a specific test method using IntelliJ's test runner. Supports any language with a run configuration provider (Java, Kotlin, Python, JavaScript, etc.)";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, METHOD_NAME, CONFIGURATION_NAME, TIMEOUT_SECONDS, PROJECT);
    }

    @Override
    public Result<ErrorResponse, Object> execute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, METHOD_NAME, CONFIGURATION_NAME, TIMEOUT_SECONDS, PROJECT)
                .mapN((filePath, methodName, configurationName, timeoutSeconds, project) -> {
                    try {
                        Path resolvedPath = filePath.resolve(project);

                        // Find PsiFile
                        VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath);
                        if (virtualFile == null) {
                            return errorResult("Error: File not found: " + filePath);
                        }
                        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(virtualFile));
                        if (psiFile == null) {
                            return errorResult("Error: Cannot parse file: " + filePath);
                        }

                        // Get run configuration candidates
                        List<RunnerAndConfigurationSettings> candidates;
                        if (methodName.isPresent()) {
                            candidates = getConfigurationsForMethod(psiFile, methodName.get());
                        } else {
                            candidates = getConfigurationsForFile(psiFile);
                        }

                        if (candidates.isEmpty()) {
                            return errorResult("Error: No run configuration found for this file" +
                                    methodName.map(m -> " and method '" + m + "'").orElse(""));
                        }

                        // Selection logic
                        RunnerAndConfigurationSettings selectedConfig;
                        if (candidates.size() == 1) {
                            selectedConfig = candidates.getFirst();
                        } else if (configurationName.isEmpty()) {
                            // Return candidate list for selection
                            List<ConfigurationCandidate> candidateList = candidates.stream()
                                    .map(c -> new ConfigurationCandidate(
                                            c.getName(),
                                            c.getType().getDisplayName()))
                                    .toList();
                            return successResult(new ConfigurationSelectionResponse(
                                    "Multiple run configurations found. Please specify 'configurationName' to select one.",
                                    candidateList));
                        } else {
                            String targetName = configurationName.get();
                            selectedConfig = candidates.stream()
                                    .filter(c -> c.getName().equals(targetName))
                                    .findFirst()
                                    .orElse(null);
                            if (selectedConfig == null) {
                                List<String> available = candidates.stream().map(RunnerAndConfigurationSettings::getName).toList();
                                return errorResult("Error: Configuration '" + targetName + "' not found. Available: " + available);
                            }
                        }

                        // Execute test
                        CompletableFuture<RunTestResponse> future = new CompletableFuture<>();
                        TestResultCollector collector = new TestResultCollector(future);

                        var connection = project.getMessageBus().connect();
                        connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, collector);

                        RunnerAndConfigurationSettings configToRun = selectedConfig;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                ExecutionUtil.runConfiguration(configToRun, DefaultRunExecutor.getRunExecutorInstance());
                            } catch (Exception e) {
                                LOG.error("Error running test configuration", e);
                                future.completeExceptionally(e);
                            }
                        });

                        try {
                            RunTestResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
                            connection.disconnect();
                            return successResult(response);
                        } catch (TimeoutException e) {
                            connection.disconnect();
                            RunTestResponse timeoutResponse = collector.buildTimeoutResponse();
                            return successResult(timeoutResponse);
                        }

                    } catch (Exception e) {
                        LOG.error("Error in run_test tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private List<RunnerAndConfigurationSettings> getConfigurationsForFile(PsiFile psiFile) {
        return ReadAction.compute(() -> {
            ConfigurationContext context = new ConfigurationContext(psiFile);
            List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
            if (configs != null && !configs.isEmpty()) {
                return configs.stream()
                        .map(ConfigurationFromContext::getConfigurationSettings)
                        .toList();
            }
            return List.of();
        });
    }

    private List<RunnerAndConfigurationSettings> getConfigurationsForMethod(PsiFile psiFile, String methodName) {
        return ReadAction.compute(() -> {
            // Find named element matching the method name
            Collection<PsiNamedElement> namedElements = PsiTreeUtil.findChildrenOfType(psiFile, PsiNamedElement.class);
            for (PsiNamedElement element : namedElements) {
                if (methodName.equals(element.getName())) {
                    ConfigurationContext context = new ConfigurationContext(element);
                    List<ConfigurationFromContext> configs = context.getConfigurationsFromContext();
                    if (configs != null && !configs.isEmpty()) {
                        return configs.stream()
                                .map(ConfigurationFromContext::getConfigurationSettings)
                                .toList();
                    }
                }
            }
            // Fallback: try file-level configs
            return getConfigurationsForFile(psiFile);
        });
    }

    private static String truncateStackTrace(String stackTrace) {
        if (stackTrace == null) return null;
        String[] lines = stackTrace.split("\n");
        if (lines.length <= MAX_STACKTRACE_LINES) return stackTrace;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_STACKTRACE_LINES; i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines[i]);
        }
        sb.append("\n... (").append(lines.length - MAX_STACKTRACE_LINES).append(" more lines)");
        return sb.toString();
    }

    // --- Inner classes ---

    private static class TestResultCollector extends SMTRunnerEventsAdapter {
        private final CompletableFuture<RunTestResponse> future;
        private final List<TestFailure> failures = Collections.synchronizedList(new ArrayList<>());
        private final StringBuilder output = new StringBuilder();

        TestResultCollector(CompletableFuture<RunTestResponse> future) {
            this.future = future;
        }

        @Override
        public void onTestFailed(SMTestProxy test) {
            String expected = null;
            String actual = null;
            DiffHyperlink diffProvider = test.getDiffViewerProvider();
            if (diffProvider != null) {
                expected = diffProvider.getLeft();
                actual = diffProvider.getRight();
            }

            failures.add(new TestFailure(
                    test.getName(),
                    test.getErrorMessage(),
                    truncateStackTrace(test.getStacktrace()),
                    expected,
                    actual
            ));

            synchronized (output) {
                output.append("[FAILED] ").append(test.getName());
                if (test.getErrorMessage() != null) {
                    output.append(": ").append(test.getErrorMessage());
                }
                output.append("\n");
            }
        }

        @Override
        public void onTestFinished(SMTestProxy test) {
            synchronized (output) {
                if (test.isPassed()) {
                    output.append("[PASSED] ").append(test.getName()).append("\n");
                }
            }
        }

        @Override
        public void onTestIgnored(SMTestProxy test) {
            synchronized (output) {
                output.append("[IGNORED] ").append(test.getName()).append("\n");
            }
        }

        @Override
        public void onTestingFinished(SMTestProxy.SMRootTestProxy root) {
            List<? extends SMTestProxy> allTests = root.getAllTests();
            // Exclude the root node itself from counting
            List<? extends SMTestProxy> leafTests = allTests.stream()
                    .filter(t -> t != root && t.getChildren().isEmpty())
                    .toList();

            int totalTests = leafTests.size();
            int passedTests = (int) leafTests.stream().filter(SMTestProxy::isPassed).count();
            int failedTests = (int) leafTests.stream().filter(t -> t.isDefect() && !t.isIgnored()).count();
            int ignoredTests = (int) leafTests.stream().filter(SMTestProxy::isIgnored).count();

            synchronized (output) {
                output.append("\nTotal: ").append(totalTests)
                        .append(", Passed: ").append(passedTests)
                        .append(", Failed: ").append(failedTests)
                        .append(", Ignored: ").append(ignoredTests);

                Long duration = root.getDuration();
                if (duration != null) {
                    output.append(", Duration: ").append(duration).append("ms");
                }
                output.append("\n");
            }

            future.complete(new RunTestResponse(
                    failedTests == 0,
                    totalTests,
                    passedTests,
                    failedTests,
                    ignoredTests,
                    false,
                    new ArrayList<>(failures),
                    output.toString()
            ));
        }

        RunTestResponse buildTimeoutResponse() {
            synchronized (output) {
                output.append("\n[TIMEOUT] Test execution timed out\n");
            }
            return new RunTestResponse(
                    false,
                    0,
                    0,
                    0,
                    0,
                    true,
                    new ArrayList<>(failures),
                    output.toString()
            );
        }
    }

    // --- Response records ---

    public record RunTestResponse(
            boolean success,
            int totalTests,
            int passedTests,
            int failedTests,
            int ignoredTests,
            boolean timedOut,
            List<TestFailure> failures,
            String output
    ) {}

    public record TestFailure(
            String testName,
            String message,
            String stackTrace,
            String expected,
            String actual
    ) {}

    public record ConfigurationSelectionResponse(
            String message,
            List<ConfigurationCandidate> availableConfigurations
    ) {}

    public record ConfigurationCandidate(
            String name,
            String type
    ) {}
}
