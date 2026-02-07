package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that renames a symbol (variable, method, class, field, etc.).
 */
public class RenameSymbolTool extends AbstractMcpTool<RenameSymbolTool.RenameSymbolResponse> {

    private static final Logger LOG = Logger.getInstance(RenameSymbolTool.class);

    @Override
    public String getName() {
        return "rename_symbol";
    }

    @Override
    public String getDescription() {
        return "Rename a symbol (variable, method, class, field, parameter) and update all references";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file containing the symbol")
                .requiredInteger("line", "Line number where the symbol is located (1-based)")
                .requiredInteger("column", "Column number where the symbol is located (1-based)")
                .requiredString("newName", "The new name for the symbol")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, RenameSymbolResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String filePath;
            int line;
            int column;
            String newName;
            String projectPath;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
                line = getIntegerArg(arguments, "line")
                        .orElseThrow(() -> new IllegalArgumentException("line is required"));
                column = getIntegerArg(arguments, "column")
                        .orElseThrow(() -> new IllegalArgumentException("column is required"));
                newName = getRequiredStringArg(arguments, "newName");
                projectPath = getRequiredStringArg(arguments, "projectPath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Find the file
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (virtualFile == null) {
                return errorResult("Error: File not found: " + filePath);
            }

            // Get PsiFile and find element
            PsiFile psiFile = runReadAction(() -> PsiManager.getInstance(project).findFile(virtualFile));
            if (psiFile == null) {
                return errorResult("Error: Cannot parse file: " + filePath);
            }

            // Calculate offset from line and column
            Document document = runReadAction(() ->
                    PsiDocumentManager.getInstance(project).getDocument(psiFile));
            if (document == null) {
                return errorResult("Error: Cannot get document for file: " + filePath);
            }

            int offset = runReadAction(() -> {
                int lineStartOffset = document.getLineStartOffset(line - 1);
                return lineStartOffset + column - 1;
            });

            // Find the element to rename
            PsiElement element = runReadAction(() -> psiFile.findElementAt(offset));
            if (element == null) {
                return errorResult("Error: No element found at position line=" + line + ", column=" + column);
            }

            // Find the named element (go up to find the actual declaration)
            PsiElement namedElement = runReadAction(() -> findNamedElement(element));
            if (namedElement == null) {
                return errorResult("Error: No renameable element found at position");
            }

            String oldName = runReadAction(() -> {
                if (namedElement instanceof PsiNamedElement named) {
                    return named.getName();
                }
                return null;
            });

            // Perform rename on EDT
            CompletableFuture<Result<ErrorResponse, RenameSymbolResponse>> future = new CompletableFuture<>();

            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    WriteCommandAction.runWriteCommandAction(project, "Rename Symbol", null, () -> {
                        RenameProcessor processor = new RenameProcessor(
                                project,
                                namedElement,
                                newName,
                                false,  // searchInComments
                                false   // searchInNonJavaFiles
                        );
                        processor.run();
                    });

                    future.complete(successResult(new RenameSymbolResponse(
                            filePath,
                            oldName,
                            newName,
                            true,
                            "Symbol renamed successfully"
                    )));
                } catch (Exception e) {
                    LOG.error("Error renaming symbol", e);
                    future.complete(errorResult("Error: " + e.getMessage()));
                }
            });

            // Wait for completion with timeout
            return future.get(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            LOG.error("Error in rename_symbol tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private PsiElement findNamedElement(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiNamedElement) {
                // Check if it's a renameable element
                if (current instanceof PsiClass ||
                    current instanceof PsiMethod ||
                    current instanceof PsiVariable) {
                    return current;
                }
            }
            // Also check if we're on a reference
            if (current instanceof PsiReference ref) {
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiNamedElement) {
                    return resolved;
                }
            }
            // Check parent for reference
            PsiElement parent = current.getParent();
            if (parent instanceof PsiReference ref) {
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiNamedElement) {
                    return resolved;
                }
            }
            current = parent;
        }
        return null;
    }

    // Response record

    public record RenameSymbolResponse(
            String filePath,
            String oldName,
            String newName,
            boolean success,
            String message
    ) {}
}
