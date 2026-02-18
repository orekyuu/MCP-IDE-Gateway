package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that finds all implementations of an interface or subclasses of a class.
 */
public class GetImplementationsTool extends AbstractMcpTool<GetImplementationsTool.GetImplementationsResponse> {

    private static final Logger LOG = Logger.getInstance(GetImplementationsTool.class);

    @Override
    public String getName() {
        return "get_implementations";
    }

    @Override
    public String getDescription() {
        return "Get all implementations of an interface or subclasses of a class";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "The class or interface name to find implementations for (simple name or fully qualified name)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalBoolean("includeAbstract", "Whether to include abstract classes in the results (default: true)")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetImplementationsResponse> execute(Map<String, Object> arguments) {
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

            boolean includeAbstract = getBooleanArg(arguments, "includeAbstract").orElse(true);

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Find the target class
            PsiClass targetClass = runReadAction(() ->
                    PsiElementResolver.findClass(project, className, GlobalSearchScope.allScope(project)));

            if (targetClass == null) {
                return errorResult("Error: Class not found: " + className);
            }

            // Get target class info
            ClassInfo targetInfo = runReadAction(() -> createClassInfo(targetClass));

            // Search for implementations using ReadAction.nonBlocking()
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            Collection<PsiClass> inheritors = ReadAction
                    .nonBlocking(() -> ClassInheritorsSearch.search(targetClass, scope, true).findAll())
                    .executeSynchronously();

            // Convert to implementation info
            List<ClassInfo> implementations = runReadAction(() -> {
                List<ClassInfo> result = new ArrayList<>();
                for (PsiClass inheritor : inheritors) {
                    // Skip abstract classes if not included
                    if (!includeAbstract && inheritor.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        continue;
                    }
                    result.add(createClassInfo(inheritor));
                }
                return result;
            });

            return successResult(new GetImplementationsResponse(targetInfo, implementations));

        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Error in get_implementations tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private ClassInfo createClassInfo(PsiClass psiClass) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String classType = PsiElementResolver.getClassKind(psiClass);
        String filePath = null;
        LineRange lineRange = null;
        List<String> modifiers = getModifiers(psiClass.getModifierList());

        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            var textRange = psiClass.getTextRange();
            if (textRange != null) {
                com.intellij.openapi.editor.Document document =
                        PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(containingFile);
                if (document != null) {
                    int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                    int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                    lineRange = new LineRange(startLine, endLine);
                }
            }
        }

        return new ClassInfo(name, qualifiedName, classType, filePath, lineRange, modifiers);
    }

    private List<String> getModifiers(PsiModifierList modifierList) {
        List<String> modifiers = new ArrayList<>();
        if (modifierList == null) {
            return modifiers;
        }

        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public");
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected");
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private");
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract");
        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final");

        return modifiers;
    }

    public record GetImplementationsResponse(
            ClassInfo target,
            List<ClassInfo> implementations
    ) {}

    public record ClassInfo(
            String name,
            String qualifiedName,
            String classType,
            String filePath,
            LineRange lineRange,
            List<String> modifiers
    ) {}
}
