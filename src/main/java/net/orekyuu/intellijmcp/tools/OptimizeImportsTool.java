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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that optimizes imports in a file.
 */
public class OptimizeImportsTool extends AbstractMcpTool<OptimizeImportsTool.OptimizeImportsResponse> {

    private static final Logger LOG = Logger.getInstance(OptimizeImportsTool.class);

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
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file to optimize imports")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .build();
    }

    @Override
    public Result<ErrorResponse, OptimizeImportsResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String filePath;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: filePath is required");
            }

            Optional<String> projectName = getStringArg(arguments, "projectName");

            // Find project
            Optional<Project> projectOpt = findProjectOrFirst(projectName.orElse(null));
            if (projectOpt.isEmpty()) {
                if (projectName.isPresent()) {
                    return errorResult("Error: Project not found: " + projectName.get());
                } else {
                    return errorResult("Error: No open projects found");
                }
            }
            Project project = projectOpt.get();

            // Find the file
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (virtualFile == null) {
                return errorResult("Error: File not found: " + filePath);
            }

            // Get PsiFile
            PsiFile psiFile = runReadAction(() -> PsiManager.getInstance(project).findFile(virtualFile));
            if (psiFile == null) {
                return errorResult("Error: Cannot parse file: " + filePath);
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
                            filePath,
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
    }

    // Response record

    public record OptimizeImportsResponse(
            String filePath,
            boolean success,
            String message
    ) {}
}
