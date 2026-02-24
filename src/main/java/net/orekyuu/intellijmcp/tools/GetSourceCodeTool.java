package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that retrieves the source code of a class or its member.
 * Returns the actual source code text along with file location information.
 */
public class GetSourceCodeTool extends AbstractProjectMcpTool<GetSourceCodeTool.GetSourceCodeResponse> {

    private static final Logger LOG = Logger.getInstance(GetSourceCodeTool.class);
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> CLASS_NAME = Arg.string("className", "Fully qualified class name (e.g., 'com.example.MyClass')").required();
    private static final Arg<Optional<String>> MEMBER_NAME = Arg.string("memberName", "Method or field name. If not specified, returns the entire class source code").optional();

    @Override
    public String getDescription() {
        return "Get the source code of a class or a specific member (method/field) by class name";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, CLASS_NAME, MEMBER_NAME);
    }

    @Override
    public Result<ErrorResponse, GetSourceCodeResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, CLASS_NAME, MEMBER_NAME)
                .mapN((project, className, memberName) -> runReadActionWithResult(() -> {
                    try {
                        // Resolve element using PsiElementResolver
                        PsiElementResolver.ResolveResult resolveResult = PsiElementResolver.resolve(project, className, memberName.orElse(null));

                        return switch (resolveResult) {
                            case PsiElementResolver.ResolveResult.ClassNotFound r ->
                                    errorResult("Error: Class not found: " + r.className());
                            case PsiElementResolver.ResolveResult.MemberNotFound r ->
                                    errorResult("Error: Member '" + r.memberName() + "' not found in class: " + r.className());
                            case PsiElementResolver.ResolveResult.Success r -> {
                                PsiElement targetElement = r.element();
                                String kind = r.kind();
                                String name = r.name();

                                // Get source code
                                String sourceCode = targetElement.getText();

                                // Get file path and line range
                                String filePath = null;
                                LineRange lineRange = null;

                                PsiFile containingFile = targetElement.getContainingFile();
                                if (containingFile != null) {
                                    VirtualFile virtualFile = containingFile.getVirtualFile();
                                    if (virtualFile != null) {
                                        filePath = virtualFile.getPath();
                                    }

                                    var textRange = targetElement.getTextRange();
                                    if (textRange != null) {
                                        com.intellij.openapi.editor.Document document =
                                                PsiDocumentManager.getInstance(project).getDocument(containingFile);
                                        if (document != null) {
                                            int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                                            int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                                            lineRange = new LineRange(startLine, endLine);
                                        }
                                    }
                                }

                                yield successResult(new GetSourceCodeResponse(
                                        name,
                                        kind,
                                        className,
                                        sourceCode,
                                        filePath,
                                        lineRange
                                ));
                            }
                        };

                    } catch (Exception e) {
                        LOG.error("Error in get_source_code tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record GetSourceCodeResponse(
            String name,
            String kind,
            String className,
            String sourceCode,
            String filePath,
            LineRange lineRange
    ) {}
}
