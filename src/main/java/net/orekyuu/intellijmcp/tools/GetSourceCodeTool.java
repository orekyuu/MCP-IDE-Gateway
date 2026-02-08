package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that retrieves the source code of a class or its member.
 * Returns the actual source code text along with file location information.
 */
public class GetSourceCodeTool extends AbstractMcpTool<GetSourceCodeTool.GetSourceCodeResponse> {

    private static final Logger LOG = Logger.getInstance(GetSourceCodeTool.class);

    @Override
    public String getName() {
        return "get_source_code";
    }

    @Override
    public String getDescription() {
        return "Get the source code of a class or a specific member (method/field) by class name";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .requiredString("className", "Fully qualified class name (e.g., 'com.example.MyClass')")
                .optionalString("memberName", "Method or field name. If not specified, returns the entire class source code")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetSourceCodeResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String projectPath;
                String className;
                try {
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                    className = getRequiredStringArg(arguments, "className");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: " + e.getMessage());
                }

                Optional<String> memberName = getStringArg(arguments, "memberName");

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Resolve element using PsiElementResolver
                PsiElementResolver.ResolveResult resolveResult = PsiElementResolver.resolve(project, className, memberName);

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
        });
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
