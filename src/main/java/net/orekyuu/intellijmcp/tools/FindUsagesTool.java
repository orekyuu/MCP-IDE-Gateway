package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Paths;
import java.util.*;

/**
 * MCP tool that finds all usages of a symbol at a given position.
 * Returns the list of locations where the symbol is referenced.
 */
public class FindUsagesTool extends AbstractMcpTool<FindUsagesTool.FindUsagesResponse> {

    private static final Logger LOG = Logger.getInstance(FindUsagesTool.class);

    @Override
    public String getName() {
        return "find_usages";
    }

    @Override
    public String getDescription() {
        return "Find all usages of a symbol at the specified file and offset position";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file containing the symbol")
                .requiredInteger("offset", "Character offset position in the file where the symbol is located")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .build();
    }

    @Override
    public Result<ErrorResponse, FindUsagesResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String filePath;
            int offset;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
                offset = getIntegerArg(arguments, "offset")
                        .orElseThrow(() -> new IllegalArgumentException("offset is required"));
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
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

            // Find the target element
            PsiElement targetElement = runReadAction(() -> {
                VirtualFile virtualFile = VirtualFileManager.getInstance()
                        .findFileByNioPath(Paths.get(filePath));
                if (virtualFile == null) {
                    return null;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile == null) {
                    return null;
                }

                // Try to find reference and resolve it
                PsiReference reference = psiFile.findReferenceAt(offset);
                if (reference != null) {
                    PsiElement resolved = reference.resolve();
                    if (resolved != null) {
                        return resolved;
                    }
                }

                // Try to find element directly (for declarations)
                PsiElement element = psiFile.findElementAt(offset);
                if (element != null) {
                    PsiElement parent = element.getParent();
                    if (parent instanceof PsiNamedElement) {
                        return parent;
                    }
                }

                return null;
            });

            if (targetElement == null) {
                return errorResult("Error: No symbol found at the specified position");
            }

            // Get symbol info
            SymbolInfo symbolInfo = runReadAction(() -> createSymbolInfo(targetElement));

            // Search for usages using ReadAction.nonBlocking()
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            Collection<PsiReference> references = ReadAction
                    .nonBlocking(() -> ReferencesSearch.search(targetElement, scope).findAll())
                    .executeSynchronously();

            // Convert references to usage info
            List<UsageInfo> usages = runReadAction(() -> {
                List<UsageInfo> result = new ArrayList<>();
                for (PsiReference ref : references) {
                    UsageInfo usage = createUsageInfo(ref);
                    if (usage != null) {
                        result.add(usage);
                    }
                }
                return result;
            });

            return successResult(new FindUsagesResponse(symbolInfo, usages));

        } catch (Exception e) {
            LOG.error("Error in find_usages tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private SymbolInfo createSymbolInfo(PsiElement element) {
        String name = null;
        String kind = "unknown";
        String containingClass = null;

        if (element instanceof PsiNamedElement namedElement) {
            name = namedElement.getName();
        }

        if (element instanceof PsiClass psiClass) {
            kind = getClassKind(psiClass);
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
        }

        return new SymbolInfo(name, kind, containingClass);
    }

    private UsageInfo createUsageInfo(PsiReference reference) {
        PsiElement element = reference.getElement();
        String filePath = null;
        LineRange lineRange = null;
        String context = null;
        String usageType = "reference";

        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile != null) {
            filePath = virtualFile.getPath();
        }

        // Get line range for the reference
        var textRange = element.getTextRange();
        if (textRange != null) {
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
            if (document != null) {
                int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                lineRange = new LineRange(startLine, endLine);

                // Get the line content as context
                int lineStartOffset = document.getLineStartOffset(startLine - 1);
                int lineEndOffset = document.getLineEndOffset(startLine - 1);
                context = document.getText().substring(lineStartOffset, lineEndOffset).trim();
            }
        }

        // Determine usage type
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            usageType = "method_call";
        } else if (parent instanceof PsiNewExpression) {
            usageType = "instantiation";
        } else if (parent instanceof PsiAssignmentExpression assignment) {
            if (assignment.getLExpression() == element ||
                PsiTreeUtil.isAncestor(assignment.getLExpression(), element, false)) {
                usageType = "write";
            } else {
                usageType = "read";
            }
        } else if (parent instanceof PsiReferenceExpression) {
            usageType = "read";
        }

        // Get containing method/class
        String containingMethod = null;
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null) {
            containingMethod = method.getName();
        }

        String containingClass = null;
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass != null) {
            containingClass = psiClass.getQualifiedName();
        }

        return new UsageInfo(filePath, lineRange, context, usageType, containingClass, containingMethod);
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

    public record FindUsagesResponse(
            SymbolInfo symbol,
            List<UsageInfo> usages
    ) {}

    public record SymbolInfo(
            String name,
            String kind,
            String containingClass
    ) {}

    public record UsageInfo(
            String filePath,
            LineRange lineRange,
            String context,
            String usageType,
            String containingClass,
            String containingMethod
    ) {}
}
