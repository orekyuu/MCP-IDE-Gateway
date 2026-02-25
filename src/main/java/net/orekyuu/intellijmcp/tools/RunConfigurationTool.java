package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RunConfigurationTool extends AbstractProjectMcpTool<Object> {

    private static final Logger LOG = Logger.getInstance(RunConfigurationTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 100000;

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> NAME =
            Arg.string("name", "Name of the run configuration to execute (use list_run_configurations to find available names)").required();
    private static final Arg<Integer> TIMEOUT_SECONDS =
            Arg.integer("timeoutSeconds", "Timeout in seconds for execution").min(0).optional(DEFAULT_TIMEOUT_SECONDS);
    private static final Arg<Integer> MAX_OUTPUT_CHARS =
            Arg.integer("maxOutputChars", "Maximum number of characters to collect from output").min(0).optional(DEFAULT_MAX_OUTPUT_CHARS);

    @Override
    public String getDescription() {
        return "Execute a run configuration by name and return its output. Use list_run_configurations first to find available configuration names. Returns stdout, stderr, exit code, and system output.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, NAME, TIMEOUT_SECONDS, MAX_OUTPUT_CHARS);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, NAME, TIMEOUT_SECONDS, MAX_OUTPUT_CHARS)
                .mapN((project, name, timeoutSeconds, maxOutputChars) -> {
                    try {
                        RunnerAndConfigurationSettings settings =
                                RunManager.getInstance(project).findConfigurationByName(name);
                        if (settings == null) {
                            return errorResult("Error: Configuration '" + name + "' not found. Use list_run_configurations to see available configurations.");
                        }

                        String configType = settings.getType().getDisplayName();
                        CompletableFuture<RunConfigurationResponse> future = new CompletableFuture<>();
                        OutputCollector collector = new OutputCollector(future, name, configType, maxOutputChars);

                        var connection = project.getMessageBus().connect();
                        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                            @Override
                            public void processStarted(@NotNull String executorId,
                                                       @NotNull ExecutionEnvironment env,
                                                       @NotNull ProcessHandler handler) {
                                RunnerAndConfigurationSettings envSettings = env.getRunnerAndConfigurationSettings();
                                if (envSettings != null && name.equals(envSettings.getName())) {
                                    handler.addProcessListener(collector);
                                }
                            }

                            @Override
                            public void processNotStarted(@NotNull String executorId,
                                                          @NotNull ExecutionEnvironment env,
                                                          Throwable cause) {
                                RunnerAndConfigurationSettings envSettings = env.getRunnerAndConfigurationSettings();
                                if (envSettings != null && name.equals(envSettings.getName())) {
                                    future.completeExceptionally(
                                            cause != null ? cause : new RuntimeException("Process failed to start"));
                                }
                            }
                        });

                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
                            } catch (Exception e) {
                                LOG.error("Error running configuration", e);
                                future.completeExceptionally(e);
                            }
                        });

                        try {
                            RunConfigurationResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
                            connection.disconnect();
                            return successResult(response);
                        } catch (TimeoutException e) {
                            connection.disconnect();
                            return successResult(collector.buildTimeoutResponse());
                        }

                    } catch (Exception e) {
                        LOG.error("Error in run_configuration tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    // --- Output collector ---

    private static class OutputCollector extends ProcessAdapter {
        private final CompletableFuture<RunConfigurationResponse> future;
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private final StringBuilder systemOutput = new StringBuilder();
        private final String configName;
        private final String configType;
        private final int maxOutputChars;

        OutputCollector(CompletableFuture<RunConfigurationResponse> future,
                        String configName, String configType, int maxOutputChars) {
            this.future = future;
            this.configName = configName;
            this.configType = configType;
            this.maxOutputChars = maxOutputChars;
        }

        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            String text = event.getText();
            if (ProcessOutputType.isStdout(outputType)) {
                appendLimited(stdout, text);
            } else if (ProcessOutputType.isStderr(outputType)) {
                appendLimited(stderr, text);
            } else {
                appendLimited(systemOutput, text);
            }
        }

        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
            future.complete(new RunConfigurationResponse(
                    false,
                    event.getExitCode(),
                    stdout.toString(),
                    stderr.toString(),
                    systemOutput.toString(),
                    configName,
                    configType
            ));
        }

        RunConfigurationResponse buildTimeoutResponse() {
            return new RunConfigurationResponse(
                    true,
                    null,
                    stdout.toString(),
                    stderr.toString(),
                    systemOutput.toString(),
                    configName,
                    configType
            );
        }

        private void appendLimited(StringBuilder sb, String text) {
            if (sb.length() >= maxOutputChars) {
                return;
            }
            int remaining = maxOutputChars - sb.length();
            if (text.length() <= remaining) {
                sb.append(text);
            } else {
                sb.append(text, 0, remaining);
                sb.append("... (output truncated)");
            }
        }
    }

    // --- Response record ---

    public record RunConfigurationResponse(
            boolean timedOut,
            Integer exitCode,
            String stdout,
            String stderr,
            String systemOutput,
            String configurationName,
            String configurationType
    ) {}
}
