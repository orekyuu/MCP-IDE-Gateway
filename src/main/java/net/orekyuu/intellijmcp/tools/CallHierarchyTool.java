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

import java.util.*;

/**
 * MCP tool that retrieves call hierarchy for a method by class name and member name.
 * Returns callers of the specified method using ReferencesSearch.
 * Uses ReadAction.nonBlocking() for better performance on large projects.
 */
public class CallHierarchyTool extends AbstractMcpTool<CallHierarchyTool.CallHierarchyResponse> {

    private static final Logger LOG = Logger.getInstance(CallHierarchyTool.class);
    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_DEPTH = 10;

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
        return JsonSchemaBuilder.object()
                .requiredString("className", "Fully qualified class name (e.g., 'com.example.MyClass')")
                .requiredString("memberName", "Method name to get call hierarchy for")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalInteger("depth", "Maximum depth of the hierarchy to retrieve (default: 3, max: 10)")
                .build();
    }

    @Override
    public Result<ErrorResponse, CallHierarchyResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String className;
            String memberName;
            String projectPath;
            try {
                className = getRequiredStringArg(arguments, "className");
                memberName = getRequiredStringArg(arguments, "memberName");
                projectPath = getRequiredStringArg(arguments, "projectPath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            int depth = getIntegerArg(arguments, "depth").orElse(DEFAULT_DEPTH);
            depth = Math.min(depth, MAX_DEPTH);

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Resolve element
            final int finalDepth = depth;
            PsiElementResolver.ResolveResult resolveResult = runReadAction(() ->
                    PsiElementResolver.resolve(project, className, Optional.of(memberName)));

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
                    MethodInfo methodInfo = buildCallHierarchy(project, method, finalDepth, visited);
                    yield successResult(new CallHierarchyResponse(methodInfo));
                }
            };

        } catch (Exception e) {
            LOG.error("Error in get_call_hierarchy tool", e);
            return errorResult("Error: " + e.getMessage());
        }
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
