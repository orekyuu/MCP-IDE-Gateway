package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that returns the type hierarchy of a class.
 * Shows superclasses, interfaces, and subclasses/implementors.
 */
public class GetTypeHierarchyTool extends AbstractMcpTool<GetTypeHierarchyTool.TypeHierarchyResponse> {

    private static final Logger LOG = Logger.getInstance(GetTypeHierarchyTool.class);
    private static final int MAX_SUBCLASSES = 50;

    @Override
    public String getName() {
        return "get_type_hierarchy";
    }

    @Override
    public String getDescription() {
        return "Get the type hierarchy of a class including superclasses, interfaces, and subclasses/implementors";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "The class name to get hierarchy for (simple name or fully qualified name)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalBoolean("includeSubclasses", "Whether to include subclasses/implementors (default: true)")
                .build();
    }

    @Override
    public Result<ErrorResponse, TypeHierarchyResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                // Get arguments
                String className;
                String projectPath;
                try {
                    className = getRequiredStringArg(arguments, "className");
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: " + e.getMessage());
                }

                boolean includeSubclasses = getBooleanArg(arguments, "includeSubclasses").orElse(true);

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Find the class
                GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                PsiClass psiClass = PsiElementResolver.findClass(project, className, scope);

                if (psiClass == null) {
                    return errorResult("Error: Class not found: " + className);
                }

                // Build hierarchy
                TypeHierarchy hierarchy = buildTypeHierarchy(psiClass, scope, includeSubclasses);

                return successResult(new TypeHierarchyResponse(hierarchy));

            } catch (Exception e) {
                LOG.error("Error in get_type_hierarchy tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private TypeHierarchy buildTypeHierarchy(PsiClass psiClass, GlobalSearchScope scope, boolean includeSubclasses) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String classType = PsiElementResolver.getClassKind(psiClass);
        String filePath = getFilePath(psiClass);
        LineRange lineRange = getLineRange(psiClass);

        // Get superclass chain
        List<TypeInfo> superclasses = new ArrayList<>();
        PsiClass current = psiClass.getSuperClass();
        while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
            superclasses.add(createTypeInfo(current));
            current = current.getSuperClass();
        }

        // Get all interfaces (including inherited)
        List<TypeInfo> interfaces = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectInterfaces(psiClass, interfaces, seen);

        // Get subclasses/implementors
        List<TypeInfo> subclasses = new ArrayList<>();
        if (includeSubclasses) {
            Collection<PsiClass> inheritors = ClassInheritorsSearch.search(psiClass, scope, true).findAll();
            int count = 0;
            for (PsiClass inheritor : inheritors) {
                if (count >= MAX_SUBCLASSES) break;
                subclasses.add(createTypeInfo(inheritor));
                count++;
            }
        }

        return new TypeHierarchy(
                name, qualifiedName, classType, filePath, lineRange,
                superclasses, interfaces, subclasses
        );
    }

    private void collectInterfaces(PsiClass psiClass, List<TypeInfo> interfaces, Set<String> seen) {
        for (PsiClass iface : psiClass.getInterfaces()) {
            String qn = iface.getQualifiedName();
            if (qn != null && !seen.contains(qn)) {
                seen.add(qn);
                interfaces.add(createTypeInfo(iface));
                // Recursively collect super-interfaces
                collectInterfaces(iface, interfaces, seen);
            }
        }
        // Also check superclass interfaces
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName())) {
            collectInterfaces(superClass, interfaces, seen);
        }
    }

    private TypeInfo createTypeInfo(PsiClass psiClass) {
        return new TypeInfo(
                psiClass.getName(),
                psiClass.getQualifiedName(),
                PsiElementResolver.getClassKind(psiClass),
                getFilePath(psiClass),
                getLineRange(psiClass)
        );
    }

    private String getFilePath(PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                return virtualFile.getPath();
            }
        }
        return null;
    }

    private LineRange getLineRange(PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        var textRange = psiClass.getTextRange();
        com.intellij.openapi.editor.Document document =
                PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(containingFile);
        if (document != null) {
            int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
            int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
            return new LineRange(startLine, endLine);
        }
        return null;
    }

    // Response and data records

    public record TypeHierarchyResponse(TypeHierarchy hierarchy) {}

    public record TypeHierarchy(
            String name,
            String qualifiedName,
            String classType,
            String filePath,
            LineRange lineRange,
            List<TypeInfo> superclasses,
            List<TypeInfo> interfaces,
            List<TypeInfo> subclasses
    ) {}

    public record TypeInfo(
            String name,
            String qualifiedName,
            String classType,
            String filePath,
            LineRange lineRange
    ) {}
}
