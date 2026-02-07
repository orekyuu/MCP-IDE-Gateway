package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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

                // Find class
                GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, scope);

                if (psiClass == null) {
                    return errorResult("Error: Class not found: " + className);
                }

                // Find target element
                PsiElement targetElement;
                String kind;
                String name;

                if (memberName.isEmpty()) {
                    targetElement = psiClass;
                    kind = getClassKind(psiClass);
                    name = psiClass.getName();
                } else {
                    String member = memberName.get();

                    // Try to find method first
                    PsiMethod[] methods = psiClass.findMethodsByName(member, false);
                    if (methods.length > 0) {
                        targetElement = methods[0];
                        kind = methods[0].isConstructor() ? "constructor" : "method";
                        name = member;
                    } else {
                        // Try to find field
                        PsiField field = psiClass.findFieldByName(member, false);
                        if (field != null) {
                            targetElement = field;
                            kind = "field";
                            name = member;
                        } else {
                            // Try to find inner class
                            PsiClass innerClass = psiClass.findInnerClassByName(member, false);
                            if (innerClass != null) {
                                targetElement = innerClass;
                                kind = getClassKind(innerClass);
                                name = member;
                            } else {
                                return errorResult("Error: Member '" + member + "' not found in class: " + className);
                            }
                        }
                    }
                }

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

                return successResult(new GetSourceCodeResponse(
                        name,
                        kind,
                        className,
                        sourceCode,
                        filePath,
                        lineRange
                ));

            } catch (Exception e) {
                LOG.error("Error in get_source_code tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private String getClassKind(PsiClass psiClass) {
        if (psiClass.isInterface()) {
            return "interface";
        } else if (psiClass.isEnum()) {
            return "enum";
        } else if (psiClass.isRecord()) {
            return "record";
        } else if (psiClass.isAnnotationType()) {
            return "annotation";
        } else {
            return "class";
        }
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
