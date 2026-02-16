package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CreateFileOrDirectoryTool extends AbstractMcpTool<CreateFileOrDirectoryTool.CreateResponse> {

    private static final Logger LOG = Logger.getInstance(CreateFileOrDirectoryTool.class);

    @Override
    public String getName() {
        return "create_file_or_directory";
    }

    @Override
    public String getDescription() {
        return "Create a file or directory in the project";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .requiredString("path", "Relative path from the project root (e.g., 'src/main/java/Foo.java')")
                .requiredBoolean("isDirectory", "true to create a directory, false to create a file")
                .optionalString("content", "Initial content for the file (ignored for directories)")
                .optionalBoolean("createParents", "Whether to create parent directories if they don't exist (default: true)")
                .optionalBoolean("overwrite", "Whether to overwrite the file if it already exists (default: false, ignored for directories)")
                .build();
    }

    @Override
    public Result<ErrorResponse, CreateResponse> execute(Map<String, Object> arguments) {
        try {
            String projectPath;
            String path;
            boolean isDirectory;
            try {
                projectPath = getRequiredStringArg(arguments, "projectPath");
                path = getRequiredStringArg(arguments, "path");
                Object isDirObj = arguments.get("isDirectory");
                if (isDirObj == null) {
                    return errorResult("Error: isDirectory is required");
                }
                isDirectory = (Boolean) isDirObj;
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            Optional<String> content = getStringArg(arguments, "content");
            boolean createParents = getBooleanArg(arguments, "createParents").orElse(true);
            boolean overwrite = getBooleanArg(arguments, "overwrite").orElse(false);

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

            CompletableFuture<Result<ErrorResponse, CreateResponse>> future = new CompletableFuture<>();

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    WriteCommandAction.runWriteCommandAction(project, "Create File or Directory", null, () -> {
                        try {
                            if (isDirectory) {
                                VirtualFile dir = VfsUtil.createDirectoryIfMissing(resolved.toString());
                                if (dir == null) {
                                    future.complete(errorResult("Error: Failed to create directory: " + path));
                                    return;
                                }
                                future.complete(successResult(new CreateResponse(path, true, true, "Directory created successfully")));
                            } else {
                                // Create parent directories if needed
                                Path parentPath = resolved.getParent();
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

                                VirtualFile parentDir = VfsUtil.findFile(resolved.getParent(), true);
                                if (parentDir == null) {
                                    future.complete(errorResult("Error: Parent directory not found for: " + path));
                                    return;
                                }

                                // Check if file already exists
                                VirtualFile existing = parentDir.findChild(resolved.getFileName().toString());
                                if (existing != null && !overwrite) {
                                    future.complete(errorResult("Error: File already exists: " + path));
                                    return;
                                }

                                VirtualFile file;
                                if (existing != null) {
                                    file = existing;
                                } else {
                                    file = parentDir.createChildData(this, resolved.getFileName().toString());
                                }
                                if (content.isPresent()) {
                                    VfsUtil.saveText(file, content.get());
                                }
                                String message = existing != null ? "File overwritten successfully" : "File created successfully";
                                future.complete(successResult(new CreateResponse(path, false, true, message)));
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
    }

    public record CreateResponse(
            String path,
            boolean isDirectory,
            boolean success,
            String message
    ) {}
}
