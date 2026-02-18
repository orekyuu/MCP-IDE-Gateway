package net.orekyuu.intellijmcp.tools;

import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that retrieves documentation for a symbol.
 */
public class GetDocumentationTool extends AbstractMcpTool<GetDocumentationTool.DocumentationResponse> {

    private static final Logger LOG = Logger.getInstance(GetDocumentationTool.class);

    @Override
    public String getName() {
        return "get_documentation";
    }

    @Override
    public String getDescription() {
        return "Get the documentation of a class, method, or field";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("symbolName", "The symbol name to get documentation for (class name, or class.method/field)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, DocumentationResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String symbolName;
                String projectPath;
                try {
                    symbolName = getRequiredStringArg(arguments, "symbolName");
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
        } else if (symbolName.contains(".") && PsiElementResolver.findClass(project, symbolName, scope) == null) {
            // Not a fully qualified class name — try to split at last dot for member access
            int lastDot = symbolName.lastIndexOf('.');
            String potentialClass = symbolName.substring(0, lastDot);
            String potentialMember = symbolName.substring(lastDot + 1);

            if (PsiElementResolver.findClass(project, potentialClass, scope) != null) {
                className = potentialClass;
                memberName = potentialMember;
            } else {
                className = symbolName;
            }
        } else {
            className = symbolName;
        }

        // Remove parentheses if present (for method references)
        if (memberName != null && memberName.contains("(")) {
            memberName = memberName.substring(0, memberName.indexOf('('));
        }

        // Resolve using PsiElementResolver
        PsiElementResolver.ResolveResult result =
                PsiElementResolver.resolve(project, className, memberName);

        return switch (result) {
            case PsiElementResolver.ResolveResult.Success s ->
                    buildDocumentation(s.element(), s.name(), getQualifiedName(s), s.kind());
            case PsiElementResolver.ResolveResult.ClassNotFound ignored -> null;
            case PsiElementResolver.ResolveResult.MemberNotFound ignored -> null;
        };
    }

    private String getQualifiedName(PsiElementResolver.ResolveResult.Success result) {
        PsiElement element = result.element();
        if (element instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }
        // For members: "containingClass#memberName"
        PsiClass parent = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (parent != null) {
            return parent.getQualifiedName() + "#" + result.name();
        }
        return result.name();
    }

    private Documentation buildDocumentation(PsiElement element, String name, String qualifiedName, String symbolType) {
        String docText = generateDocumentation(element);
        return new Documentation(name, qualifiedName, symbolType, docText, getFilePath(element), getLineRange(element));
    }

    private String generateDocumentation(PsiElement element) {
        DocumentationProvider provider = LanguageDocumentation.INSTANCE.forLanguage(element.getLanguage());
        if (provider == null) {
            return null;
        }
        String html = provider.generateDoc(element, null);
        return html != null ? stripHtml(html) : null;
    }

    private String stripHtml(String html) {
        // Convert block-level elements to newlines
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)</tr>", "\n");
        text = text.replaceAll("(?i)<li>", "- ");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Decode common HTML entities
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");
        text = text.replace("&nbsp;", " ");

        // Normalize whitespace: collapse multiple spaces/tabs on same line
        text = text.replaceAll("[ \\t]+", " ");
        // Collapse 3+ consecutive newlines into 2
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.strip();
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

    // Response and data records

    public record DocumentationResponse(Documentation documentation) {}

    public record Documentation(
            String name,
            String qualifiedName,
            String symbolType,
            String documentation,
            String filePath,
            LineRange lineRange
    ) {}
}
