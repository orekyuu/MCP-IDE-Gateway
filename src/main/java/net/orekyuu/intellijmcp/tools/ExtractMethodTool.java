package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool that extracts selected code into a new method.
 */
public class ExtractMethodTool extends AbstractMcpTool<ExtractMethodTool.ExtractMethodResponse> {

    private static final Logger LOG = Logger.getInstance(ExtractMethodTool.class);

    @Override
    public String getName() {
        return "extract_method";
    }

    @Override
    public String getDescription() {
        return "Extract a range of code into a new method";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file")
                .requiredInteger("startLine", "Start line of the code to extract (1-based)")
                .requiredInteger("endLine", "End line of the code to extract (1-based)")
                .requiredString("methodName", "Name for the new method")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, ExtractMethodResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String filePath;
            int startLine;
            int endLine;
            String methodName;
            String projectPath;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
                startLine = getIntegerArg(arguments, "startLine")
                        .orElseThrow(() -> new IllegalArgumentException("startLine is required"));
                endLine = getIntegerArg(arguments, "endLine")
                        .orElseThrow(() -> new IllegalArgumentException("endLine is required"));
                methodName = getRequiredStringArg(arguments, "methodName");
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

            // Get PsiFile
            PsiFile psiFile = runReadAction(() -> PsiManager.getInstance(project).findFile(virtualFile));
            if (psiFile == null) {
                return errorResult("Error: Cannot parse file: " + filePath);
            }

            if (!(psiFile instanceof PsiJavaFile)) {
                return errorResult("Error: Extract method is only supported for Java files");
            }

            // Get document and calculate offsets
            Document document = runReadAction(() ->
                    PsiDocumentManager.getInstance(project).getDocument(psiFile));
            if (document == null) {
                return errorResult("Error: Cannot get document for file: " + filePath);
            }

            int startOffset = runReadAction(() -> document.getLineStartOffset(startLine - 1));
            int endOffset = runReadAction(() -> document.getLineEndOffset(endLine - 1));

            // Find the elements in the range
            PsiElement[] elementsInRange = runReadAction(() -> findElementsInRange(psiFile, startOffset, endOffset));

            if (elementsInRange == null || elementsInRange.length == 0) {
                return errorResult("Error: No extractable code found in the specified range");
            }

            // Perform extract method on EDT
            CompletableFuture<Result<ErrorResponse, ExtractMethodResponse>> future = new CompletableFuture<>();

            ApplicationManager.getApplication().invokeLater(() -> {
                Editor editor = null;
                try {
                    // Create a temporary editor for the extraction
                    editor = EditorFactory.getInstance().createEditor(document, project);
                    editor.getSelectionModel().setSelection(startOffset, endOffset);

                    final Editor finalEditor = editor;
                    final PsiElement[] finalElements = elementsInRange;

                    WriteCommandAction.runWriteCommandAction(project, "Extract Method", null, () -> {
                        try {
                            ExtractMethodProcessor processor = new ExtractMethodProcessor(
                                    project,
                                    finalEditor,
                                    finalElements,
                                    null,  // forcedReturnType
                                    "Extract Method",
                                    methodName,
                                    "refactoring.extractMethod"  // helpId
                            );

                            if (processor.prepare()) {
                                processor.testPrepare();
                                if (processor.showDialog()) {
                                    processor.doRefactoring();
                                    future.complete(successResult(new ExtractMethodResponse(
                                            filePath,
                                            methodName,
                                            startLine,
                                            endLine,
                                            true,
                                            "Method extracted successfully"
                                    )));
                                } else {
                                    // Dialog was cancelled or not shown, try direct extraction
                                    processor.doRefactoring();
                                    future.complete(successResult(new ExtractMethodResponse(
                                            filePath,
                                            methodName,
                                            startLine,
                                            endLine,
                                            true,
                                            "Method extracted successfully"
                                    )));
                                }
                            } else {
                                future.complete(errorResult("Error: Cannot extract method from the specified range"));
                            }
                        } catch (PrepareFailedException e) {
                            future.complete(errorResult("Error: " + e.getMessage()));
                        } catch (Exception e) {
                            LOG.error("Error during extraction", e);
                            future.complete(errorResult("Error: " + e.getMessage()));
                        }
                    });
                } catch (Exception e) {
                    LOG.error("Error extracting method", e);
                    future.complete(errorResult("Error: " + e.getMessage()));
                } finally {
                    if (editor != null) {
                        final Editor editorToRelease = editor;
                        ApplicationManager.getApplication().invokeLater(() ->
                            EditorFactory.getInstance().releaseEditor(editorToRelease)
                        );
                    }
                }
            });

            // Wait for completion with timeout
            return future.get(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            LOG.error("Error in extract_method tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private PsiElement[] findElementsInRange(PsiFile psiFile, int startOffset, int endOffset) {
        List<PsiElement> elements = new ArrayList<>();

        PsiElement startElement = psiFile.findElementAt(startOffset);
        if (startElement == null) {
            return null;
        }

        // Find the statement or expression containing the start
        PsiStatement startStatement = PsiTreeUtil.getParentOfType(startElement, PsiStatement.class, false);
        if (startStatement == null) {
            // Try to find expression
            PsiExpression expr = PsiTreeUtil.getParentOfType(startElement, PsiExpression.class, false);
            if (expr != null) {
                return new PsiElement[]{expr};
            }
            return null;
        }

        // Get parent block
        PsiElement parent = startStatement.getParent();
        if (!(parent instanceof PsiCodeBlock)) {
            return new PsiElement[]{startStatement};
        }

        // Collect all statements in range
        for (PsiStatement stmt : ((PsiCodeBlock) parent).getStatements()) {
            int stmtStart = stmt.getTextRange().getStartOffset();
            int stmtEnd = stmt.getTextRange().getEndOffset();

            if (stmtStart >= startOffset && stmtEnd <= endOffset) {
                elements.add(stmt);
            } else if (stmtStart < endOffset && stmtEnd > startOffset) {
                // Partially overlapping - include it
                elements.add(stmt);
            }
        }

        return elements.isEmpty() ? null : elements.toArray(new PsiElement[0]);
    }

    // Response record

    public record ExtractMethodResponse(
            String filePath,
            String methodName,
            int startLine,
            int endLine,
            boolean success,
            String message
    ) {}
}
