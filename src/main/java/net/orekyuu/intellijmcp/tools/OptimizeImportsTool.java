package net.orekyuu.intellijmcp.tools;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that optimizes imports in a file.
 */
public class OptimizeImportsTool extends AbstractProjectMcpTool<OptimizeImportsTool.OptimizeImportsResponse> {

    private static final Logger LOG = Logger.getInstance(OptimizeImportsTool.class);

    private static final Arg<ProjectRelativePath> FILE_PATH =
            Arg.projectRelativePath("filePath", "Relative path to the file to optimize imports");
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getName() {
        return "optimize_imports";
    }

    @Override
    public String getDescription() {
        return "Optimize imports in a file (remove unused imports and organize)";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, PROJECT);
    }

    @Override
    public Result<ErrorResponse, OptimizeImportsResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, PROJECT)
                .mapN((filePath, project) -> {
                    try {
                        Path resolvedPath = filePath.resolve(project);
                        String absolutePath = resolvedPath.toString();

                        // Find the file
                        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath);
                        if (virtualFile == null) {
                            return errorResult("Error: File not found: " + absolutePath);
                        }

                        // Get PsiFile
                        PsiFile psiFile = runReadAction(() -> PsiManager.getInstance(project).findFile(virtualFile));
                        if (psiFile == null) {
                            return errorResult("Error: Cannot parse file: " + absolutePath);
                        }

                        // Run optimize imports on EDT
                        CompletableFuture<Result<ErrorResponse, OptimizeImportsResponse>> future = new CompletableFuture<>();

                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                WriteCommandAction.runWriteCommandAction(project, "Optimize Imports", null, () -> {
                                    OptimizeImportsProcessor processor = new OptimizeImportsProcessor(project, psiFile);
                                    processor.run();
                                });

                                future.complete(successResult(new OptimizeImportsResponse(
                                        absolutePath,
                                        true,
                                        "Imports optimized successfully"
                                )));
                            } catch (Exception e) {
                                LOG.error("Error optimizing imports", e);
                                future.complete(errorResult("Error: " + e.getMessage()));
                            }
                        });

                        // Wait for completion with timeout
                        return future.get(30, TimeUnit.SECONDS);

                    } catch (Exception e) {
                        LOG.error("Error in optimize_imports tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    // Response record

    public record OptimizeImportsResponse(
            String filePath,
            boolean success,
            String message
    ) {}
}
