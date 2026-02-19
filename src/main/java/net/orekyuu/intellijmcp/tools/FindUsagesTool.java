package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.*;

/**
 * MCP tool that finds all usages of a symbol by class name and optional member name.
 * Returns the list of locations where the symbol is referenced.
 */
public class FindUsagesTool extends AbstractMcpTool<FindUsagesTool.FindUsagesResponse> {

    private static final Logger LOG = Logger.getInstance(FindUsagesTool.class);
    private static final Arg<String> CLASS_NAME = Arg.string("className", "Fully qualified class name (e.g., 'com.example.MyClass')").required();
    private static final Arg<Optional<String>> MEMBER_NAME = Arg.string("memberName", "Method, field, or inner class name. If not specified, finds usages of the class itself").optional();
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getName() {
        return "find_usages";
    }

    @Override
    public String getDescription() {
        return "Find all usages of a symbol by class name and optional member name";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(CLASS_NAME, MEMBER_NAME, PROJECT);
    }

    @Override
    public Result<ErrorResponse, FindUsagesResponse> execute(Map<String, Object> arguments) {
        return Args.validate(arguments, CLASS_NAME, MEMBER_NAME, PROJECT)
                .mapN((className, memberName, project) -> {
                    try {
                        // Resolve the target element
                        PsiElement targetElement = runReadAction(() -> {
                            PsiElementResolver.ResolveResult result = PsiElementResolver.resolve(project, className, memberName.orElse(null));
                            if (result instanceof PsiElementResolver.ResolveResult.Success s) {
                                return s.element();
                            }
                            return null;
                        });

                        if (targetElement == null) {
                            PsiElementResolver.ResolveResult result = runReadAction(() -> PsiElementResolver.resolve(project, className, memberName.orElse(null)));
                            return switch (result) {
                                case PsiElementResolver.ResolveResult.ClassNotFound r ->
                                        errorResult("Error: Class not found: " + r.className());
                                case PsiElementResolver.ResolveResult.MemberNotFound r ->
                                        errorResult("Error: Member '" + r.memberName() + "' not found in class: " + r.className());
                                case PsiElementResolver.ResolveResult.Success ignored ->
                                        errorResult("Error: Unexpected state");
                            };
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

                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        throw e;
                    } catch (Exception e) {
                        LOG.error("Error in find_usages tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private SymbolInfo createSymbolInfo(PsiElement element) {
        String name = null;
        String kind = "unknown";
        String containingClass = null;

        if (element instanceof PsiNamedElement namedElement) {
            name = namedElement.getName();
        }

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

        // Determine usage type based on the resolved target element (language-independent)
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiMethod) {
            usageType = "call";
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
