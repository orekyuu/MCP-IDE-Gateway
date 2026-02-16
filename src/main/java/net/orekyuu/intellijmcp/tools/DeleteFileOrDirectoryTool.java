package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DeleteFileOrDirectoryTool extends AbstractMcpTool<DeleteFileOrDirectoryTool.DeleteResponse> {

    private static final Logger LOG = Logger.getInstance(DeleteFileOrDirectoryTool.class);

    @Override
    public String getName() {
        return "delete_file_or_directory";
    }

    @Override
    public String getDescription() {
        return "Delete a file or directory in the project";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .requiredString("path", "Relative path from the project root")
                .optionalBoolean("recursive", "Whether to recursively delete directory contents (default: false)")
                .build();
    }

    @Override
    public Result<ErrorResponse, DeleteResponse> execute(Map<String, Object> arguments) {
        try {
            String projectPath;
            String path;
            try {
                projectPath = getRequiredStringArg(arguments, "projectPath");
                path = getRequiredStringArg(arguments, "path");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            boolean recursive = getBooleanArg(arguments, "recursive").orElse(false);

            // Validate path is within project
            Path resolved = Paths.get(projectPath).resolve(path).normalize();
            if (!resolved.startsWith(Paths.get(projectPath).normalize())) {
                return errorResult("Error: Path is outside the project directory");
            }

            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Find the file
            VirtualFile file = VirtualFileManager.getInstance().findFileByNioPath(resolved);
            if (file == null || !file.exists()) {
                return errorResult("Error: File or directory not found: " + path);
            }

            boolean isDirectory = file.isDirectory();

            // Check if directory is non-empty and recursive is false
            if (isDirectory && !recursive) {
                VirtualFile[] children = file.getChildren();
                if (children != null && children.length > 0) {
                    return errorResult("Error: Directory is not empty. Use recursive=true to delete non-empty directories");
                }
            }

            CompletableFuture<Result<ErrorResponse, DeleteResponse>> future = new CompletableFuture<>();

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    WriteCommandAction.runWriteCommandAction(project, "Delete File or Directory", null, () -> {
                        try {
                            file.delete(this);
                            future.complete(successResult(new DeleteResponse(
                                    path,
                                    isDirectory,
                                    true,
                                    (isDirectory ? "Directory" : "File") + " deleted successfully"
                            )));
                        } catch (Exception e) {
                            LOG.error("Error deleting file or directory", e);
                            future.complete(errorResult("Error: " + e.getMessage()));
                        }
                    });
                } catch (Exception e) {
                    LOG.error("Error in delete_file_or_directory tool", e);
                    future.complete(errorResult("Error: " + e.getMessage()));
                }
            });

            return future.get(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            LOG.error("Error in delete_file_or_directory tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    public record DeleteResponse(
            String path,
            boolean isDirectory,
            boolean success,
            String message
    ) {}
}
