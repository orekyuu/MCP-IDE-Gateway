package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.*;

public class ListTestConfigurationsTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<ProjectRelativePath> FILE_PATH =
            Arg.projectRelativePath("filePath", "Relative path to the test file from the project root");
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getDescription() {
        return "List available test configurations for a file. Returns configurations for the whole file and for each individual test name found in the file.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, PROJECT);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, PROJECT)
                .mapN((filePath, project) -> {
                    try {
                        Path resolvedPath = filePath.resolve(project);

                        VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath);
                        if (virtualFile == null) {
                            return errorResult("Error: File not found: " + filePath);
                        }
                        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(virtualFile));
                        if (psiFile == null) {
                            return errorResult("Error: Cannot parse file: " + filePath);
                        }

                        RunTestTool runTestTool = new RunTestTool();

                        // File-level configurations
                        List<RunnerAndConfigurationSettings> fileConfigs = runTestTool.getConfigurationsForFile(psiFile);
                        List<ConfigurationInfo> fileConfigInfos = fileConfigs.stream()
                                .map(c -> new ConfigurationInfo(c.getName(), c.getType().getDisplayName()))
                                .toList();

                        // Per-test configurations
                        List<String> testNames = ReadAction.compute(() -> {
                            Collection<PsiNamedElement> namedElements = PsiTreeUtil.findChildrenOfType(psiFile, PsiNamedElement.class);
                            return namedElements.stream()
                                    .map(PsiNamedElement::getName)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .toList();
                        });

                        List<TestConfigurationEntry> testEntries = new ArrayList<>();
                        for (String testName : testNames) {
                            List<RunnerAndConfigurationSettings> testConfigs = runTestTool.getConfigurationsForTest(psiFile, testName);
                            if (!testConfigs.isEmpty()) {
                                List<ConfigurationInfo> configInfos = testConfigs.stream()
                                        .map(c -> new ConfigurationInfo(c.getName(), c.getType().getDisplayName()))
                                        .toList();
                                testEntries.add(new TestConfigurationEntry(testName, configInfos));
                            }
                        }

                        return successResult(new ListTestConfigurationsResponse(fileConfigInfos, testEntries));

                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    // --- Response records ---

    public record ListTestConfigurationsResponse(
            List<ConfigurationInfo> fileConfigurations,
            List<TestConfigurationEntry> testConfigurations
    ) {}

    public record TestConfigurationEntry(
            String testName,
            List<ConfigurationInfo> configurations
    ) {}

    public record ConfigurationInfo(
            String name,
            String type
    ) {}
}
