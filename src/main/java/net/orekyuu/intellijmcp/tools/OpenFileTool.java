package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that opens a file in the IntelliJ IDEA editor.
 * Supports specifying a target project or using the first available project.
 */
public class OpenFileTool extends AbstractMcpTool {

    private static final Logger LOG = Logger.getInstance(OpenFileTool.class);

    @Override
    public String getName() {
        return "open_file";
    }

    @Override
    public String getDescription() {
        return "Open a file in IntelliJ IDEA editor";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file to open")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .build();
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
        try {
            // Get required file path
            String filePath;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: filePath is required");
            }

            // Get optional project name
            Optional<String> projectName = getStringArg(arguments, "projectName");

            // Find target project
            Optional<Project> targetProject = findProjectOrFirst(projectName.orElse(null));

            if (targetProject.isEmpty()) {
                if (projectName.isPresent()) {
                    return errorResult("Error: Project not found: " + projectName.get());
                } else {
                    return errorResult("Error: No open projects found");
                }
            }

            // Find the file
            VirtualFile file = runReadAction(() ->
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath))
            );

            if (file == null) {
                return errorResult("Error: File not found: " + filePath);
            }

            // Open file on EDT
            Project project = targetProject.get();
            runOnEdt(() -> {
                LOG.info("Open file: " + filePath + " in project: " + project.getName());
                FileEditorManager.getInstance(project).openFile(file, true);
            });

            return successResult("File opened successfully: " + filePath);
        } catch (Exception e) {
            LOG.error("Error in open_file tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
