package net.orekyuu.intellijmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * MCP tool that retrieves call hierarchy for a method at a given location.
 * Returns callers of the specified method using ReferencesSearch.
 * Uses ReadAction.nonBlocking() for better performance on large projects.
 */
public class CallHierarchyTool extends AbstractMcpTool {

    private static final Logger LOG = Logger.getInstance(CallHierarchyTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_DEPTH = 10;

    @Override
    public String getName() {
        return "get_call_hierarchy";
    }

    @Override
    public String getDescription() {
        return "Get call hierarchy (callers) for a method at the specified file and offset position";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file containing the method")
                .requiredInteger("offset", "Character offset position in the file where the method is located")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .optionalInteger("depth", "Maximum depth of the hierarchy to retrieve (default: 3, max: 10)")
                .build();
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
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
            int depth = getIntegerArg(arguments, "depth").orElse(DEFAULT_DEPTH);
            depth = Math.min(depth, MAX_DEPTH);

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

            // Find file and method using ReadAction
            final int finalDepth = depth;
            PsiMethod method = runReadAction(() -> {
                VirtualFile virtualFile = VirtualFileManager.getInstance()
                        .findFileByNioPath(Paths.get(filePath));
                if (virtualFile == null) {
                    return null;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile == null) {
                    return null;
                }

                PsiElement element = psiFile.findElementAt(offset);
                if (element == null) {
                    return null;
                }

                PsiMethod foundMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
                if (foundMethod == null) {
                    PsiReference reference = psiFile.findReferenceAt(offset);
                    if (reference != null) {
                        PsiElement resolved = reference.resolve();
                        if (resolved instanceof PsiMethod) {
                            foundMethod = (PsiMethod) resolved;
                        }
                    }
                }
                return foundMethod;
            });

            if (method == null) {
                return errorResult("Error: No method found at the specified position. " +
                        "Make sure the offset points to a method declaration or reference.");
            }

            // Build call hierarchy using ReadAction.nonBlocking() for heavy search operations
            Set<PsiMethod> visited = new HashSet<>();
            ObjectNode result = buildCallHierarchy(project, method, finalDepth, visited);
            return successResult(MAPPER.writeValueAsString(result));

        } catch (JsonProcessingException e) {
            LOG.error("Error serializing JSON in get_call_hierarchy tool", e);
            return errorResult("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error in get_call_hierarchy tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private ObjectNode buildCallHierarchy(Project project, PsiMethod method, int maxDepth, Set<PsiMethod> visited) {
        // Create method info in read action
        ObjectNode root = ReadAction.compute(() -> createMethodInfo(method));

        if (maxDepth > 0) {
            ArrayNode callers = findCallers(project, method, maxDepth, 1, visited);
            root.set("callers", callers);
        }

        return root;
    }

    private ArrayNode findCallers(Project project, PsiMethod method, int maxDepth, int currentDepth, Set<PsiMethod> visited) {
        ArrayNode callers = MAPPER.createArrayNode();

        if (visited.contains(method)) {
            return callers; // Avoid infinite loops
        }
        visited.add(method);

        try {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            // Collect all methods to search (including super methods from interfaces)
            Set<PsiMethod> methodsToSearch = ReadAction.compute(() -> {
                Set<PsiMethod> methods = new LinkedHashSet<>();
                methods.add(method);
                // Add super methods (interface methods that this method implements)
                for (PsiMethod superMethod : method.findSuperMethods()) {
                    methods.add(superMethod);
                }
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
            Collection<PsiReference> references = allReferences;

            // Process references in a read action
            Set<PsiMethod> callerMethods = ReadAction.compute(() -> {
                Set<PsiMethod> methods = new LinkedHashSet<>();
                for (PsiReference ref : references) {
                    PsiElement refElement = ref.getElement();
                    PsiMethod callerMethod = PsiTreeUtil.getParentOfType(refElement, PsiMethod.class, false);

                    if (callerMethod != null && !callerMethod.equals(method) && !methods.contains(callerMethod)) {
                        methods.add(callerMethod);
                    }
                }
                return methods;
            });

            for (PsiMethod callerMethod : callerMethods) {
                ObjectNode callerInfo = ReadAction.compute(() -> createMethodInfo(callerMethod));

                // Recursively find callers if not at max depth
                if (currentDepth < maxDepth && !visited.contains(callerMethod)) {
                    ArrayNode nestedCallers = findCallers(project, callerMethod, maxDepth, currentDepth + 1, visited);
                    if (nestedCallers.size() > 0) {
                        callerInfo.set("callers", nestedCallers);
                    }
                }

                callers.add(callerInfo);
            }
        } catch (Exception e) {
            LOG.warn("Error finding callers for method: " + method.getName(), e);
        }

        return callers;
    }

    private ObjectNode createMethodInfo(PsiMethod method) {
        ObjectNode info = MAPPER.createObjectNode();
        info.put("name", method.getName());

        // Get containing class
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            info.put("className", containingClass.getQualifiedName());
        }

        // Get file path and line number
        PsiFile containingFile = method.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                info.put("filePath", virtualFile.getPath());
            }

            // Get line number
            int offset = method.getTextOffset();
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(method.getProject()).getDocument(containingFile);
            if (document != null) {
                int lineNumber = document.getLineNumber(offset) + 1; // 1-indexed
                info.put("lineNumber", lineNumber);
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
        info.put("signature", signature.toString());

        return info;
    }
}
