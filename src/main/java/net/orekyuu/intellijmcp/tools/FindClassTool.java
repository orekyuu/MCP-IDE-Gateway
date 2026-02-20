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
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.*;

/**
 * MCP tool that finds classes by name in the project.
 * Supports both simple class names and fully qualified names.
 */
public class FindClassTool extends AbstractProjectMcpTool<FindClassTool.FindClassResponse> {

    private static final Logger LOG = Logger.getInstance(FindClassTool.class);

    private static final Arg<String> CLASS_NAME =
            Arg.string("className", "The class name to search for (simple name or fully qualified name)").required();
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Boolean> INCLUDE_LIBRARIES =
            Arg.bool("includeLibraries", "Whether to include library classes in the search").optional(false);

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
        return Args.schema(CLASS_NAME, PROJECT, INCLUDE_LIBRARIES);
    }

    @Override
    public Result<ErrorResponse, FindClassResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, CLASS_NAME, PROJECT, INCLUDE_LIBRARIES)
                .mapN((className, project, includeLibraries) -> runReadActionWithResult(() -> {
                    try {
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
                                foundClasses.add(createClassInfo(psiClass));
                            }
                        } else {
                            // Search by simple name
                            PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope);
                            for (PsiClass psiClass : classes) {
                                foundClasses.add(createClassInfo(psiClass));
                            }
                        }

                        return successResult(new FindClassResponse(className, foundClasses));

                    } catch (Exception e) {
                        LOG.error("Error in find_class tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private ClassInfo createClassInfo(PsiClass psiClass) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String filePath = null;
        LineRange lineRange = null;
        String classType = PsiElementResolver.getClassKind(psiClass);

        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            // Get line range
            var textRange = psiClass.getTextRange();
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(containingFile);
            if (document != null) {
                int startLine = document.getLineNumber(textRange.getStartOffset()) + 1; // 1-indexed
                int endLine = document.getLineNumber(textRange.getEndOffset()) + 1; // 1-indexed
                lineRange = new LineRange(startLine, endLine);
            }
        }

        return new ClassInfo(name, qualifiedName, filePath, classType, lineRange);
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
            String classType,
            LineRange lineRange
    ) {}
}
