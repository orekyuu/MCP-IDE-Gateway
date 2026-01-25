package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that finds classes by name in the project.
 * Supports both simple class names and fully qualified names.
 */
public class FindClassTool extends AbstractMcpTool<FindClassTool.FindClassResponse> {

    private static final Logger LOG = Logger.getInstance(FindClassTool.class);

    @Override
    public String getName() {
        return "find_class";
    }

    @Override
    public String getDescription() {
        return "Find classes by name in the project. Supports simple names (e.g., 'MyClass') or fully qualified names (e.g., 'com.example.MyClass')";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "The class name to search for (simple name or fully qualified name)")
                .optionalString("projectName", "Name of the project (optional, uses first project if not specified)")
                .optionalBoolean("includeLibraries", "Whether to include library classes in the search (default: false)")
                .build();
    }

    @Override
    public Result<ErrorResponse, FindClassResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String className;
                try {
                    className = getRequiredStringArg(arguments, "className");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: className is required");
                }

                Optional<String> projectName = getStringArg(arguments, "projectName");
                boolean includeLibraries = getBooleanArg(arguments, "includeLibraries").orElse(false);

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

                // Determine search scope
                GlobalSearchScope scope = includeLibraries
                        ? GlobalSearchScope.allScope(project)
                        : GlobalSearchScope.projectScope(project);

                // Search for classes
                List<ClassInfo> foundClasses = new ArrayList<>();

                // Check if it's a fully qualified name (contains a dot)
                if (className.contains(".")) {
                    // Search by fully qualified name
                    PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(className, scope);
                    for (PsiClass psiClass : classes) {
                        ClassInfo info = createClassInfo(psiClass);
                        if (info != null) {
                            foundClasses.add(info);
                        }
                    }
                } else {
                    // Search by simple name
                    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope);
                    for (PsiClass psiClass : classes) {
                        ClassInfo info = createClassInfo(psiClass);
                        if (info != null) {
                            foundClasses.add(info);
                        }
                    }
                }

                return successResult(new FindClassResponse(className, foundClasses));

            } catch (Exception e) {
                LOG.error("Error in find_class tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private ClassInfo createClassInfo(PsiClass psiClass) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String filePath = null;
        Integer lineNumber = null;
        String classType = getClassType(psiClass);

        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            // Get line number
            int offset = psiClass.getTextOffset();
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(containingFile);
            if (document != null) {
                lineNumber = document.getLineNumber(offset) + 1; // 1-indexed
            }
        }

        return new ClassInfo(name, qualifiedName, filePath, lineNumber, classType);
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

    /**
     * Response containing the search results.
     */
    public record FindClassResponse(
            String searchQuery,
            List<ClassInfo> classes
    ) {}

    /**
     * Information about a found class.
     */
    public record ClassInfo(
            String name,
            String qualifiedName,
            String filePath,
            Integer lineNumber,
            String classType
    ) {}
}
