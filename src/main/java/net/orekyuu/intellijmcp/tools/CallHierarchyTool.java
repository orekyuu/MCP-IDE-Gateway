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
 * MCP tool that retrieves call hierarchy for a method by class name and member name.
 * Returns callers of the specified method using ReferencesSearch.
 * Uses ReadAction.nonBlocking() for better performance on large projects.
 */
public class CallHierarchyTool extends AbstractProjectMcpTool<CallHierarchyTool.CallHierarchyResponse> {

    private static final Logger LOG = Logger.getInstance(CallHierarchyTool.class);

    private static final Arg<String> CLASS_NAME =
            Arg.string("className", "Fully qualified class name (e.g., 'com.example.MyClass')").required();
    private static final Arg<String> MEMBER_NAME =
            Arg.string("memberName", "Method name to get call hierarchy for").required();
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Integer> DEPTH =
            Arg.integer("depth", "Maximum depth of the hierarchy to retrieve").max(10).optional(3);

    @Override
    public String getName() {
        return "get_call_hierarchy";
    }

    @Override
    public String getDescription() {
        return "Get call hierarchy (callers) for a method by class name and method name";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(CLASS_NAME, MEMBER_NAME, PROJECT, DEPTH);
    }

    @Override
    public Result<ErrorResponse, CallHierarchyResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, CLASS_NAME, MEMBER_NAME, PROJECT, DEPTH)
                .mapN((className, memberName, project, depth) -> {
                    try {
                        // Resolve element
                        PsiElementResolver.ResolveResult resolveResult = runReadAction(() ->
                                PsiElementResolver.resolve(project, className, memberName));

                        return switch (resolveResult) {
                            case PsiElementResolver.ResolveResult.ClassNotFound r ->
                                    errorResult("Error: Class not found: " + r.className());
                            case PsiElementResolver.ResolveResult.MemberNotFound r ->
                                    errorResult("Error: Member '" + r.memberName() + "' not found in class: " + r.className());
                            case PsiElementResolver.ResolveResult.Success r -> {
                                if (!(r.element() instanceof PsiMethod method)) {
                                    yield errorResult("Error: '" + memberName + "' is not a method. Call hierarchy is only available for methods.");
                                }

                                // Build call hierarchy
                                Set<PsiMethod> visited = new HashSet<>();
                                MethodInfo methodInfo = buildCallHierarchy(project, method, depth, visited);
                                yield successResult(new CallHierarchyResponse(methodInfo));
                            }
                        };

                    } catch (Exception e) {
                        LOG.error("Error in get_call_hierarchy tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private MethodInfo buildCallHierarchy(Project project, PsiMethod method, int maxDepth, Set<PsiMethod> visited) {
        // Create method info in read action
        MethodInfo baseInfo = ReadAction.compute(() -> createMethodInfo(method));

        List<MethodInfo> callers = Collections.emptyList();
        if (maxDepth > 0) {
            callers = findCallers(project, method, maxDepth, 1, visited);
        }

        return baseInfo.withCallers(callers);
    }

    private List<MethodInfo> findCallers(Project project, PsiMethod method, int maxDepth, int currentDepth, Set<PsiMethod> visited) {
        if (visited.contains(method)) {
            return Collections.emptyList(); // Avoid infinite loops
        }
        visited.add(method);

        List<MethodInfo> callers = new ArrayList<>();

        try {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            // Collect all methods to search (including super methods from interfaces)
            Set<PsiMethod> methodsToSearch = ReadAction.compute(() -> {
                Set<PsiMethod> methods = new LinkedHashSet<>();
                methods.add(method);
                // Add super methods (interface methods that this method implements)
                Collections.addAll(methods, method.findSuperMethods());
                return methods;
            });

            // Use ReadAction.nonBlocking() for the heavy search operation
            Set<PsiReference> allReferences = new LinkedHashSet<>();
            for (PsiMethod m : methodsToSearch) {
                Collection<PsiReference> refs = ReadAction
                        .nonBlocking(() -> ReferencesSearch.search(m, scope).findAll())
                        .executeSynchronously();
                allReferences.addAll(refs);
            }

            // Process references in a read action
            Set<PsiMethod> callerMethods = ReadAction.compute(() -> {
                Set<PsiMethod> methods = new LinkedHashSet<>();
                for (PsiReference ref : allReferences) {
                    PsiElement refElement = ref.getElement();
                    PsiMethod callerMethod = PsiTreeUtil.getParentOfType(refElement, PsiMethod.class, false);

                    if (callerMethod != null && !callerMethod.equals(method)) {
                        methods.add(callerMethod);
                    }
                }
                return methods;
            });

            for (PsiMethod callerMethod : callerMethods) {
                MethodInfo callerInfo = ReadAction.compute(() -> createMethodInfo(callerMethod));

                // Recursively find callers if not at max depth
                if (currentDepth < maxDepth && !visited.contains(callerMethod)) {
                    List<MethodInfo> nestedCallers = findCallers(project, callerMethod, maxDepth, currentDepth + 1, visited);
                    if (!nestedCallers.isEmpty()) {
                        callerInfo = callerInfo.withCallers(nestedCallers);
                    }
                }

                callers.add(callerInfo);
            }
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Error finding callers for method: " + method.getName(), e);
        }

        return callers;
    }

    private MethodInfo createMethodInfo(PsiMethod method) {
        String name = method.getName();
        String className = null;
        String filePath = null;
        LineRange lineRange = null;

        // Get containing class
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            className = containingClass.getQualifiedName();
        }

        // Get file path and line range
        PsiFile containingFile = method.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            // Get line range
            var textRange = method.getTextRange();
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(method.getProject()).getDocument(containingFile);
            if (document != null) {
                int startLine = document.getLineNumber(textRange.getStartOffset()) + 1; // 1-indexed
                int endLine = document.getLineNumber(textRange.getEndOffset()) + 1; // 1-indexed
                lineRange = new LineRange(startLine, endLine);
            }
        }

        // Get method signature
        StringBuilder signature = new StringBuilder();
        signature.append(method.getName()).append("(");
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) signature.append(", ");
            signature.append(parameters[i].getType().getPresentableText());
        }
        signature.append(")");

        return new MethodInfo(name, className, filePath, signature.toString(), lineRange, Collections.emptyList());
    }

    /**
     * Response containing the call hierarchy for a method.
     */
    public record CallHierarchyResponse(MethodInfo method) {}

    /**
     * Information about a method in the call hierarchy.
     */
    public record MethodInfo(
            String name,
            String className,
            String filePath,
            String signature,
            LineRange lineRange,
            List<MethodInfo> callers
    ) {
        /**
         * Returns a new MethodInfo with the given callers.
         */
        public MethodInfo withCallers(List<MethodInfo> callers) {
            return new MethodInfo(name, className, filePath, signature, lineRange, callers);
        }
    }
}
