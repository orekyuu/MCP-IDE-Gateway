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
 * MCP tool that returns the structure of a class.
 * Shows fields, methods, constructors, and inner classes.
 */
public class GetClassStructureTool extends AbstractMcpTool<GetClassStructureTool.ClassStructureResponse> {

    private static final Logger LOG = Logger.getInstance(GetClassStructureTool.class);

    @Override
    public String getName() {
        return "get_class_structure";
    }

    @Override
    public String getDescription() {
        return "Get the structure of a class including fields, methods, constructors, and inner classes";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("className", "The class name to get structure for (simple name or fully qualified name)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalBoolean("includeInherited", "Whether to include inherited members (default: false)")
                .build();
    }

    @Override
    public Result<ErrorResponse, ClassStructureResponse> execute(Map<String, Object> arguments) {
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

                boolean includeInherited = getBooleanArg(arguments, "includeInherited").orElse(false);

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Find the class
                GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                PsiClass psiClass = findClass(project, className, scope);

                if (psiClass == null) {
                    return errorResult("Error: Class not found: " + className);
                }

                // Build structure
                ClassStructure structure = buildClassStructure(psiClass, includeInherited);

                return successResult(new ClassStructureResponse(structure));

            } catch (Exception e) {
                LOG.error("Error in get_class_structure tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private PsiClass findClass(Project project, String className, GlobalSearchScope scope) {
        // Try fully qualified name first
        if (className.contains(".")) {
            PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(className, scope);
            if (classes.length > 0) {
                return classes[0];
            }
        }

        // Try simple name
        PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope);
        if (classes.length > 0) {
            return classes[0];
        }

        return null;
    }

    private ClassStructure buildClassStructure(PsiClass psiClass, boolean includeInherited) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String classType = getClassType(psiClass);
        String filePath = null;
        LineRange lineRange = null;

        // Get file info
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile != null) {
            VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
                filePath = virtualFile.getPath();
            }

            var textRange = psiClass.getTextRange();
            com.intellij.openapi.editor.Document document =
                    PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(containingFile);
            if (document != null) {
                int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
                int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
                lineRange = new LineRange(startLine, endLine);
            }
        }

        // Get superclass and interfaces
        String superClass = null;
        PsiClass superPsiClass = psiClass.getSuperClass();
        if (superPsiClass != null && !"java.lang.Object".equals(superPsiClass.getQualifiedName())) {
            superClass = superPsiClass.getQualifiedName();
        }

        List<String> interfaces = new ArrayList<>();
        for (PsiClass iface : psiClass.getInterfaces()) {
            interfaces.add(iface.getQualifiedName());
        }

        // Get modifiers
        List<String> modifiers = getModifiers(psiClass.getModifierList());

        // Get fields
        List<FieldInfo> fields = new ArrayList<>();
        PsiField[] psiFields = includeInherited ? psiClass.getAllFields() : psiClass.getFields();
        for (PsiField field : psiFields) {
            fields.add(createFieldInfo(field, psiClass));
        }

        // Get constructors
        List<MethodInfo> constructors = new ArrayList<>();
        for (PsiMethod constructor : psiClass.getConstructors()) {
            constructors.add(createMethodInfo(constructor, psiClass));
        }

        // Get methods
        List<MethodInfo> methods = new ArrayList<>();
        PsiMethod[] psiMethods = includeInherited ? psiClass.getAllMethods() : psiClass.getMethods();
        for (PsiMethod method : psiMethods) {
            if (!method.isConstructor()) {
                methods.add(createMethodInfo(method, psiClass));
            }
        }

        // Get inner classes
        List<InnerClassInfo> innerClasses = new ArrayList<>();
        for (PsiClass innerClass : psiClass.getInnerClasses()) {
            innerClasses.add(createInnerClassInfo(innerClass));
        }

        return new ClassStructure(
                name, qualifiedName, classType, filePath, lineRange,
                modifiers, superClass, interfaces,
                fields, constructors, methods, innerClasses
        );
    }

    private FieldInfo createFieldInfo(PsiField field, PsiClass containingClass) {
        String name = field.getName();
        String type = field.getType().getPresentableText();
        List<String> modifiers = getModifiers(field.getModifierList());
        boolean inherited = !field.getContainingClass().equals(containingClass);
        LineRange lineRange = getLineRange(field);

        return new FieldInfo(name, type, modifiers, inherited, lineRange);
    }

    private MethodInfo createMethodInfo(PsiMethod method, PsiClass containingClass) {
        String name = method.getName();
        String returnType = method.isConstructor() ? null :
                method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void";
        List<String> modifiers = getModifiers(method.getModifierList());
        boolean inherited = !method.getContainingClass().equals(containingClass);
        LineRange lineRange = getLineRange(method);

        // Get parameters
        List<ParameterInfo> parameters = new ArrayList<>();
        for (PsiParameter param : method.getParameterList().getParameters()) {
            parameters.add(new ParameterInfo(param.getName(), param.getType().getPresentableText()));
        }

        return new MethodInfo(name, returnType, parameters, modifiers, inherited, lineRange);
    }

    private InnerClassInfo createInnerClassInfo(PsiClass innerClass) {
        String name = innerClass.getName();
        String classType = getClassType(innerClass);
        List<String> modifiers = getModifiers(innerClass.getModifierList());
        LineRange lineRange = getLineRange(innerClass);

        return new InnerClassInfo(name, classType, modifiers, lineRange);
    }

    private LineRange getLineRange(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        var textRange = element.getTextRange();
        com.intellij.openapi.editor.Document document =
                PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
        if (document != null) {
            int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
            int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
            return new LineRange(startLine, endLine);
        }
        return null;
    }

    private List<String> getModifiers(PsiModifierList modifierList) {
        List<String> modifiers = new ArrayList<>();
        if (modifierList == null) {
            return modifiers;
        }

        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public");
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected");
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private");
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static");
        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final");
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract");
        if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers.add("synchronized");
        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) modifiers.add("volatile");
        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) modifiers.add("transient");
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) modifiers.add("native");

        return modifiers;
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

    // Response and data records

    public record ClassStructureResponse(ClassStructure structure) {}

    public record ClassStructure(
            String name,
            String qualifiedName,
            String classType,
            String filePath,
            LineRange lineRange,
            List<String> modifiers,
            String superClass,
            List<String> interfaces,
            List<FieldInfo> fields,
            List<MethodInfo> constructors,
            List<MethodInfo> methods,
            List<InnerClassInfo> innerClasses
    ) {}

    public record FieldInfo(
            String name,
            String type,
            List<String> modifiers,
            boolean inherited,
            LineRange lineRange
    ) {}

    public record MethodInfo(
            String name,
            String returnType,
            List<ParameterInfo> parameters,
            List<String> modifiers,
            boolean inherited,
            LineRange lineRange
    ) {}

    public record ParameterInfo(
            String name,
            String type
    ) {}

    public record InnerClassInfo(
            String name,
            String classType,
            List<String> modifiers,
            LineRange lineRange
    ) {}
}