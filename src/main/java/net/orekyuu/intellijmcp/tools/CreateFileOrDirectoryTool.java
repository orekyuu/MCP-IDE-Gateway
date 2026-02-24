package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CreateFileOrDirectoryTool extends AbstractProjectMcpTool<CreateFileOrDirectoryTool.CreateResponse> {

    private static final Logger LOG = Logger.getInstance(CreateFileOrDirectoryTool.class);

    private static final Arg<ProjectRelativePath> PATH =
            Arg.projectRelativePath("path", "Relative path from the project root (e.g., 'src/main/java/Foo.java')");
    private static final Arg<Boolean> IS_DIRECTORY =
            Arg.bool("isDirectory", "true to create a directory, false to create a file").required();
    private static final Arg<Optional<String>> CONTENT =
            Arg.string("content", "Initial content for the file (ignored for directories)").optional();
    private static final Arg<Boolean> CREATE_PARENTS =
            Arg.bool("createParents", "Whether to create parent directories if they don't exist").optional(true);
    private static final Arg<Boolean> OVERWRITE =
            Arg.bool("overwrite", "Whether to overwrite the file if it already exists").optional(false);
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getDescription() {
        return "Create a file or directory in the project";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PATH, IS_DIRECTORY, CONTENT, CREATE_PARENTS, OVERWRITE, PROJECT);
    }

    @Override
    public Result<ErrorResponse, CreateResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PATH, IS_DIRECTORY, CONTENT, CREATE_PARENTS, OVERWRITE, PROJECT)
                .mapN((path, isDirectory, content, createParents, overwrite, project) -> {
                    try {
                        Path resolvedPath = path.resolve(project);

                        CompletableFuture<Result<ErrorResponse, CreateResponse>> future = new CompletableFuture<>();

                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                WriteCommandAction.runWriteCommandAction(project, "Create File or Directory", null, () -> {
                                    try {
                                        if (isDirectory) {
                                            VirtualFile dir = VfsUtil.createDirectoryIfMissing(resolvedPath.toString());
                                            if (dir == null) {
                                                future.complete(errorResult("Error: Failed to create directory: " + path));
                                                return;
                                            }
                                            future.complete(successResult(new CreateResponse(resolvedPath.toString(), true, true, "Directory created successfully")));
                                        } else {
                                            // Create parent directories if needed
                                            Path parentPath = resolvedPath.getParent();
                                            if (parentPath != null && createParents) {
                                                VirtualFile parentDir = VfsUtil.createDirectoryIfMissing(parentPath.toString());
                                                if (parentDir == null) {
                                                    future.complete(errorResult("Error: Failed to create parent directories for: " + path));
                                                    return;
                                                }
                                            } else if (parentPath != null) {
                                                VirtualFile parentDir = VfsUtil.findFile(parentPath, true);
                                                if (parentDir == null || !parentDir.exists()) {
                                                    future.complete(errorResult("Error: Parent directory does not exist: " + parentPath));
                                                    return;
                                                }
                                            }

                                            Path resolvedParent = resolvedPath.getParent();
                                            if (resolvedParent == null) {
                                                future.complete(errorResult("Error: Cannot determine parent directory for: " + path));
                                                return;
                                            }
                                            VirtualFile parentDir = VfsUtil.findFile(resolvedParent, true);
                                            if (parentDir == null) {
                                                future.complete(errorResult("Error: Parent directory not found for: " + path));
                                                return;
                                            }

                                            // Check if file already exists
                                            VirtualFile existing = parentDir.findChild(resolvedPath.getFileName().toString());
                                            if (existing != null && !overwrite) {
                                                future.complete(errorResult("Error: File already exists: " + path));
                                                return;
                                            }

                                            VirtualFile file;
                                            if (existing != null) {
                                                file = existing;
                                            } else {
                                                file = parentDir.createChildData(this, resolvedPath.getFileName().toString());
                                            }
                                            if (content.isPresent()) {
                                                VfsUtil.saveText(file, content.get());
                                            }
                                            String message = existing != null ? "File overwritten successfully" : "File created successfully";
                                            future.complete(successResult(new CreateResponse(resolvedPath.toString(), false, true, message)));
                                        }
                                    } catch (Exception e) {
                                        LOG.error("Error creating file or directory", e);
                                        future.complete(errorResult("Error: " + e.getMessage()));
                                    }
                                });
                            } catch (Exception e) {
                                LOG.error("Error in create_file_or_directory tool", e);
                                future.complete(errorResult("Error: " + e.getMessage()));
                            }
                        });

                        return future.get(30, TimeUnit.SECONDS);

                    } catch (Exception e) {
                        LOG.error("Error in create_file_or_directory tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record CreateResponse(
            String path,
            boolean isDirectory,
            boolean success,
            String message
    ) {}
}
