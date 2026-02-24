package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.*;

/**
 * MCP tool that returns the structure of a class.
 * Shows fields, methods, constructors, and inner classes.
 */
public class GetClassStructureTool extends AbstractProjectMcpTool<GetClassStructureTool.ClassStructureResponse> {

    private static final Logger LOG = Logger.getInstance(GetClassStructureTool.class);

    private static final Arg<String> CLASS_NAME =
            Arg.string("className", "The class name to get structure for (simple name or fully qualified name)").required();
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Boolean> INCLUDE_INHERITED =
            Arg.bool("includeInherited", "Whether to include inherited members").optional(false);

    @Override
    public String getDescription() {
        return "Get the structure of a class including fields, methods, constructors, and inner classes";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(CLASS_NAME, PROJECT, INCLUDE_INHERITED);
    }

    @Override
    public Result<ErrorResponse, ClassStructureResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, CLASS_NAME, PROJECT, INCLUDE_INHERITED)
                .mapN((className, project, includeInherited) -> runReadActionWithResult(() -> {
                    try {
                        // Find the class
                        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                        PsiClass psiClass = PsiElementResolver.findClass(project, className, scope);

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
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private ClassStructure buildClassStructure(PsiClass psiClass, boolean includeInherited) {
        String name = psiClass.getName();
        String qualifiedName = psiClass.getQualifiedName();
        String classType = PsiElementResolver.getClassKind(psiClass);
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
        PsiClass fieldClass = field.getContainingClass();
        boolean inherited = fieldClass != null && !fieldClass.equals(containingClass);
        LineRange lineRange = getLineRange(field);

        return new FieldInfo(name, type, modifiers, inherited, lineRange);
    }

    private MethodInfo createMethodInfo(PsiMethod method, PsiClass containingClass) {
        String name = method.getName();
        String returnType = method.isConstructor() ? null :
                method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void";
        List<String> modifiers = getModifiers(method.getModifierList());
        PsiClass methodClass = method.getContainingClass();
        boolean inherited = methodClass != null && !methodClass.equals(containingClass);
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
        String classType = PsiElementResolver.getClassKind(innerClass);
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