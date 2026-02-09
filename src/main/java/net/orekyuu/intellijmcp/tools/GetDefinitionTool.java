package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that retrieves the definition location of a symbol by class name and optional member name.
 * Returns the file path, line range, and symbol information where the symbol is defined.
 */
public class GetDefinitionTool extends AbstractMcpTool<GetDefinitionTool.GetDefinitionResponse> {

    private static final Logger LOG = Logger.getInstance(GetDefinitionTool.class);

    @Override
    public String getName() {
        return "get_definition";
    }

    @Override
    public String getDescription() {
        return "Get the definition location of a symbol by class name and optional member name";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "Fully qualified class name (e.g., 'com.example.MyClass')")
                .optionalString("memberName", "Method, field, or inner class name. If not specified, returns the class definition")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetDefinitionResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String className;
                String projectPath;
                try {
                    className = getRequiredStringArg(arguments, "className");
                    projectPath = getRequiredStringArg(arguments, "projectPath");
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

                // Resolve element
                PsiElementResolver.ResolveResult resolveResult = PsiElementResolver.resolve(project, className, memberName);

                return switch (resolveResult) {
                    case PsiElementResolver.ResolveResult.ClassNotFound r ->
                            errorResult("Error: Class not found: " + r.className());
                    case PsiElementResolver.ResolveResult.MemberNotFound r ->
                            errorResult("Error: Member '" + r.memberName() + "' not found in class: " + r.className());
                    case PsiElementResolver.ResolveResult.Success r -> {
                        DefinitionInfo info = createDefinitionInfo(r.element());
                        if (info == null) {
                            yield errorResult("Error: Could not get definition information");
                        }
                        yield successResult(new GetDefinitionResponse(info));
                    }
                };

            } catch (Exception e) {
                LOG.error("Error in get_definition tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private DefinitionInfo createDefinitionInfo(PsiElement element) {
        String name = null;
        String kind = "unknown";
        String containingClass = null;
        String filePath = null;
        LineRange lineRange = null;

        // Get name
        if (element instanceof PsiNamedElement namedElement) {
            name = namedElement.getName();
        }

        // Get kind and container
        if (element instanceof PsiClass psiClass) {
            kind = PsiElementResolver.getClassKind(psiClass);
            PsiClass parent = psiClass.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiMethod method) {
            kind = method.isConstructor() ? "constructor" : "method";
            PsiClass parent = method.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiField field) {
            kind = "field";
            PsiClass parent = field.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiParameter) {
            kind = "parameter";
        } else if (element instanceof PsiLocalVariable) {
            kind = "local_variable";
        } else if (element instanceof PsiPackage) {
            kind = "package";
            name = ((PsiPackage) element).getQualifiedName();
        }

        // Get file path and line range
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            var textRange = element.getTextRange();
            if (textRange != null) {
                com.intellij.openapi.editor.Document document =
                        PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
                if (document != null) {
                    int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                    int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                    lineRange = new LineRange(startLine, endLine);
                }
            }
        }

        if (name == null && filePath == null) {
            return null;
        }

        return new DefinitionInfo(name, kind, containingClass, filePath, lineRange);
    }

    public record GetDefinitionResponse(DefinitionInfo definition) {}

    public record DefinitionInfo(
            String name,
            String kind,
            String containingClass,
            String filePath,
            LineRange lineRange
    ) {}
}
