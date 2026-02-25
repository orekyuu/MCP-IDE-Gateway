package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.List;
import java.util.Map;

public class ListRunConfigurationsTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Boolean> INCLUDE_TEMPORARY =
            Arg.bool("includeTemporary", "Whether to include temporary run configurations (auto-generated during test runs)").optional(false);

    @Override
    public String getDescription() {
        return "List all run configurations in the project. Use this to find available configurations before calling run_configuration. Returns configuration names, types, and whether they are temporary.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, INCLUDE_TEMPORARY);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, INCLUDE_TEMPORARY)
                .mapN((project, includeTemporary) -> runReadActionWithResult(() -> {
                    try {
                        RunManager runManager = RunManager.getInstance(project);
                        List<RunnerAndConfigurationSettings> allSettings = runManager.getAllSettings();

                        List<RunConfigurationInfo> configurations = allSettings.stream()
                                .filter(settings -> includeTemporary || !settings.isTemporary())
                                .map(settings -> new RunConfigurationInfo(
                                        settings.getName(),
                                        settings.getType().getDisplayName(),
                                        settings.getType().getId(),
                                        settings.isTemporary()
                                ))
                                .toList();

                        return successResult(new ListRunConfigurationsResponse(configurations));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    // --- Response records ---

    public record ListRunConfigurationsResponse(
            List<RunConfigurationInfo> configurations
    ) {}

    public record RunConfigurationInfo(
            String name,
            String type,
            String typeId,
            boolean isTemporary
    ) {}
}
