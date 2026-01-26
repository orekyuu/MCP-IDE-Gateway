package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that retrieves documentation (Javadoc) for a symbol.
 */
public class GetDocumentationTool extends AbstractMcpTool<GetDocumentationTool.DocumentationResponse> {

    private static final Logger LOG = Logger.getInstance(GetDocumentationTool.class);

    @Override
    public String getName() {
        return "get_documentation";
    }

    @Override
    public String getDescription() {
        return "Get the documentation (Javadoc) of a class, method, or field";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("symbolName", "The symbol name to get documentation for (class name, or class.method/field)")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .build();
    }

    @Override
    public Result<ErrorResponse, DocumentationResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String symbolName;
                try {
                    symbolName = getRequiredStringArg(arguments, "symbolName");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: symbolName is required");
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

                GlobalSearchScope scope = GlobalSearchScope.allScope(project);

                // Parse the symbol name
                Documentation doc = findDocumentation(project, symbolName, scope);

                if (doc == null) {
                    return errorResult("Error: Symbol not found or has no documentation: " + symbolName);
                }

                return successResult(new DocumentationResponse(doc));

            } catch (Exception e) {
                LOG.error("Error in get_documentation tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private Documentation findDocumentation(Project project, String symbolName, GlobalSearchScope scope) {
        // Check if it's a member reference (class.member or class#member)
        String className;
        String memberName = null;

        if (symbolName.contains("#")) {
            String[] parts = symbolName.split("#", 2);
            className = parts[0];
            memberName = parts[1];
        } else if (symbolName.contains(".") && !isFullyQualifiedClassName(project, symbolName, scope)) {
            // Try to split at last dot for member access
            int lastDot = symbolName.lastIndexOf('.');
            String potentialClass = symbolName.substring(0, lastDot);
            String potentialMember = symbolName.substring(lastDot + 1);

            PsiClass psiClass = findClass(project, potentialClass, scope);
            if (psiClass != null) {
                className = potentialClass;
                memberName = potentialMember;
            } else {
                className = symbolName;
            }
        } else {
            className = symbolName;
        }

        // Find the class
        PsiClass psiClass = findClass(project, className, scope);
        if (psiClass == null) {
            return null;
        }

        if (memberName != null) {
            // Find member (method or field)
            return findMemberDocumentation(psiClass, memberName);
        } else {
            // Return class documentation
            return extractClassDocumentation(psiClass);
        }
    }

    private boolean isFullyQualifiedClassName(Project project, String name, GlobalSearchScope scope) {
        if (!name.contains(".")) return false;
        PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(name, scope);
        return classes.length > 0;
    }

    private PsiClass findClass(Project project, String className, GlobalSearchScope scope) {
        // Try fully qualified name first
        if (className.contains(".")) {
            PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(className, scope);
            if (classes.length > 0) {
                return classes[0];
            }
        }

        // Try simple name
        PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope);
        if (classes.length > 0) {
            return classes[0];
        }

        return null;
    }

    private Documentation findMemberDocumentation(PsiClass psiClass, String memberName) {
        // Remove parentheses if present (for method references)
        String baseName = memberName.contains("(") ? memberName.substring(0, memberName.indexOf('(')) : memberName;

        // Try methods first
        for (PsiMethod method : psiClass.getMethods()) {
            if (method.getName().equals(baseName)) {
                Documentation doc = extractMethodDocumentation(method);
                if (doc != null) {
                    return doc;
                }
            }
        }

        // Try fields
        PsiField field = psiClass.findFieldByName(baseName, false);
        if (field != null) {
            return extractFieldDocumentation(field);
        }

        return null;
    }

    private Documentation extractClassDocumentation(PsiClass psiClass) {
        PsiDocComment docComment = psiClass.getDocComment();
        if (docComment == null) {
            // Return basic info even without javadoc
            return new Documentation(
                    psiClass.getName(),
                    psiClass.getQualifiedName(),
                    "class",
                    null,
                    Collections.emptyList(),
                    getFilePath(psiClass),
                    getLineRange(psiClass)
            );
        }

        String description = extractDescription(docComment);
        List<DocTag> tags = extractTags(docComment);

        return new Documentation(
                psiClass.getName(),
                psiClass.getQualifiedName(),
                getClassType(psiClass),
                description,
                tags,
                getFilePath(psiClass),
                getLineRange(psiClass)
        );
    }

    private Documentation extractMethodDocumentation(PsiMethod method) {
        PsiDocComment docComment = method.getDocComment();

        String signature = buildMethodSignature(method);
        PsiClass parent = method.getContainingClass();
        String qualifiedName = parent != null ? parent.getQualifiedName() + "#" + method.getName() : method.getName();

        if (docComment == null) {
            return new Documentation(
                    method.getName(),
                    qualifiedName,
                    "method",
                    null,
                    Collections.emptyList(),
                    getFilePath(method),
                    getLineRange(method)
            );
        }

        String description = extractDescription(docComment);
        List<DocTag> tags = extractTags(docComment);

        return new Documentation(
                method.getName(),
                qualifiedName,
                "method",
                description,
                tags,
                getFilePath(method),
                getLineRange(method)
        );
    }

    private Documentation extractFieldDocumentation(PsiField field) {
        PsiDocComment docComment = field.getDocComment();

        PsiClass parent = field.getContainingClass();
        String qualifiedName = parent != null ? parent.getQualifiedName() + "#" + field.getName() : field.getName();

        if (docComment == null) {
            return new Documentation(
                    field.getName(),
                    qualifiedName,
                    "field",
                    null,
                    Collections.emptyList(),
                    getFilePath(field),
                    getLineRange(field)
            );
        }

        String description = extractDescription(docComment);
        List<DocTag> tags = extractTags(docComment);

        return new Documentation(
                field.getName(),
                qualifiedName,
                "field",
                description,
                tags,
                getFilePath(field),
                getLineRange(field)
        );
    }

    private String extractDescription(PsiDocComment docComment) {
        StringBuilder sb = new StringBuilder();
        for (PsiElement element : docComment.getDescriptionElements()) {
            String text = element.getText().trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private List<DocTag> extractTags(PsiDocComment docComment) {
        List<DocTag> tags = new ArrayList<>();
        for (PsiDocTag tag : docComment.getTags()) {
            String name = tag.getName();
            String value = getTagValue(tag);
            tags.add(new DocTag(name, value));
        }
        return tags;
    }

    private String getTagValue(PsiDocTag tag) {
        StringBuilder sb = new StringBuilder();
        for (PsiElement element : tag.getDataElements()) {
            sb.append(element.getText());
        }
        return sb.toString().trim();
    }

    private String buildMethodSignature(PsiMethod method) {
        StringBuilder sb = new StringBuilder();

        // Return type
        if (!method.isConstructor()) {
            PsiType returnType = method.getReturnType();
            sb.append(returnType != null ? returnType.getPresentableText() : "void");
            sb.append(" ");
        }

        // Method name and parameters
        sb.append(method.getName()).append("(");
        PsiParameter[] params = method.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getType().getPresentableText());
            sb.append(" ").append(params[i].getName());
        }
        sb.append(")");

        return sb.toString();
    }

    private String getFilePath(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                return virtualFile.getPath();
            }
        }
        return null;
    }

    private LineRange getLineRange(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        var textRange = element.getTextRange();
        com.intellij.openapi.editor.Document document =
                PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
        if (document != null) {
            int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
            int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
            return new LineRange(startLine, endLine);
        }
        return null;
    }

    private String getClassType(PsiClass psiClass) {
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

    // Response and data records

    public record DocumentationResponse(Documentation documentation) {}

    public record Documentation(
            String name,
            String qualifiedName,
            String symbolType,
            String description,
            List<DocTag> tags,
            String filePath,
            LineRange lineRange
    ) {}

    public record DocTag(
            String name,
            String value
    ) {}
}
