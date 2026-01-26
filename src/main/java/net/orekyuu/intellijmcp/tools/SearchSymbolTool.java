package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that searches for symbols (methods, fields, classes) by name.
 */
public class SearchSymbolTool extends AbstractMcpTool<SearchSymbolTool.SearchSymbolResponse> {

    private static final Logger LOG = Logger.getInstance(SearchSymbolTool.class);
    private static final int MAX_RESULTS = 50;

    @Override
    public String getName() {
        return "search_symbol";
    }

    @Override
    public String getDescription() {
        return "Search for symbols (methods, fields, classes) by name in the project";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("query", "The symbol name to search for (supports partial matching)")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .optionalString("symbolType", "Type of symbol to search for: 'all', 'class', 'method', 'field' (default: 'all')")
                .build();
    }

    @Override
    public Result<ErrorResponse, SearchSymbolResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String query;
                try {
                    query = getRequiredStringArg(arguments, "query");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: query is required");
                }

                Optional<String> projectName = getStringArg(arguments, "projectName");
                String symbolType = getStringArg(arguments, "symbolType").orElse("all");

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

                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
                PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

                List<SymbolInfo> symbols = new ArrayList<>();

                // Search classes
                if ("all".equals(symbolType) || "class".equals(symbolType)) {
                    searchClasses(cache, query, scope, symbols);
                }

                // Search methods
                if ("all".equals(symbolType) || "method".equals(symbolType)) {
                    searchMethods(cache, query, scope, symbols);
                }

                // Search fields
                if ("all".equals(symbolType) || "field".equals(symbolType)) {
                    searchFields(cache, query, scope, symbols);
                }

                // Limit results
                if (symbols.size() > MAX_RESULTS) {
                    symbols = symbols.subList(0, MAX_RESULTS);
                }

                return successResult(new SearchSymbolResponse(query, symbols.size(), symbols));

            } catch (Exception e) {
                LOG.error("Error in search_symbol tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private void searchClasses(PsiShortNamesCache cache, String query, GlobalSearchScope scope, List<SymbolInfo> results) {
        // Get all class names and filter
        String[] allClassNames = cache.getAllClassNames();
        for (String className : allClassNames) {
            if (results.size() >= MAX_RESULTS) break;

            if (matchesQuery(className, query)) {
                PsiClass[] classes = cache.getClassesByName(className, scope);
                for (PsiClass psiClass : classes) {
                    if (results.size() >= MAX_RESULTS) break;

                    SymbolInfo info = createSymbolInfo(psiClass);
                    if (info != null) {
                        results.add(info);
                    }
                }
            }
        }
    }

    private void searchMethods(PsiShortNamesCache cache, String query, GlobalSearchScope scope, List<SymbolInfo> results) {
        String[] allMethodNames = cache.getAllMethodNames();
        for (String methodName : allMethodNames) {
            if (results.size() >= MAX_RESULTS) break;

            if (matchesQuery(methodName, query)) {
                PsiMethod[] methods = cache.getMethodsByName(methodName, scope);
                for (PsiMethod method : methods) {
                    if (results.size() >= MAX_RESULTS) break;

                    SymbolInfo info = createSymbolInfo(method);
                    if (info != null) {
                        results.add(info);
                    }
                }
            }
        }
    }

    private void searchFields(PsiShortNamesCache cache, String query, GlobalSearchScope scope, List<SymbolInfo> results) {
        String[] allFieldNames = cache.getAllFieldNames();
        for (String fieldName : allFieldNames) {
            if (results.size() >= MAX_RESULTS) break;

            if (matchesQuery(fieldName, query)) {
                PsiField[] fields = cache.getFieldsByName(fieldName, scope);
                for (PsiField field : fields) {
                    if (results.size() >= MAX_RESULTS) break;

                    SymbolInfo info = createSymbolInfo(field);
                    if (info != null) {
                        results.add(info);
                    }
                }
            }
        }
    }

    private boolean matchesQuery(String name, String query) {
        if (name == null) return false;
        // Case-insensitive contains match
        return name.toLowerCase().contains(query.toLowerCase());
    }

    private SymbolInfo createSymbolInfo(PsiElement element) {
        String name = null;
        String kind = "unknown";
        String containingClass = null;
        String signature = null;
        String filePath = null;
        LineRange lineRange = null;

        if (element instanceof PsiClass psiClass) {
            name = psiClass.getName();
            kind = getClassKind(psiClass);
            PsiClass parent = psiClass.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
        } else if (element instanceof PsiMethod method) {
            name = method.getName();
            kind = method.isConstructor() ? "constructor" : "method";
            PsiClass parent = method.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
            signature = buildMethodSignature(method);
        } else if (element instanceof PsiField field) {
            name = field.getName();
            kind = "field";
            PsiClass parent = field.getContainingClass();
            if (parent != null) {
                containingClass = parent.getQualifiedName();
            }
            signature = field.getType().getPresentableText() + " " + field.getName();
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

        if (name == null) {
            return null;
        }

        return new SymbolInfo(name, kind, containingClass, signature, filePath, lineRange);
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
        }
        sb.append(")");

        return sb.toString();
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

    public record SearchSymbolResponse(
            String query,
            int totalFound,
            List<SymbolInfo> symbols
    ) {}

    public record SymbolInfo(
            String name,
            String kind,
            String containingClass,
            String signature,
            String filePath,
            LineRange lineRange
    ) {}
}
