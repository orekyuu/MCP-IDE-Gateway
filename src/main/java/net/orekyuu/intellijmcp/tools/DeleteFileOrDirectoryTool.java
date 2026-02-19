package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DeleteFileOrDirectoryTool extends AbstractMcpTool<DeleteFileOrDirectoryTool.DeleteResponse> {

    private static final Logger LOG = Logger.getInstance(DeleteFileOrDirectoryTool.class);

    private static final Arg<ProjectRelativePath> PATH =
            Arg.projectRelativePath("path", "Relative path from the project root");
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Boolean> RECURSIVE =
            Arg.bool("recursive", "Whether to recursively delete directory contents").optional(false);

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
        return Args.schema(PATH, PROJECT, RECURSIVE);
    }

    @Override
    public Result<ErrorResponse, DeleteResponse> execute(Map<String, Object> arguments) {
        return Args.validate(arguments, PATH, PROJECT, RECURSIVE)
                .mapN((path, project, recursive) -> {
                    try {
                        Path resolvedPath = path.resolve(project);

                        // Find the file
                        VirtualFile file = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath);
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
                                                resolvedPath.toString(),
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
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record DeleteResponse(
            String path,
            boolean isDirectory,
            boolean success,
            String message
    ) {}
}
