package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for resolving PsiElements from className + memberName.
 */
public final class PsiElementResolver {

    private PsiElementResolver() {}

    public sealed interface ResolveResult permits ResolveResult.Success, ResolveResult.ClassNotFound, ResolveResult.MemberNotFound {
        record Success(PsiElement element, String kind, String name) implements ResolveResult {}
        record ClassNotFound(String className) implements ResolveResult {}
        record MemberNotFound(String memberName, String className) implements ResolveResult {}
    }

    /**
     * Resolves a PsiElement from className and optional memberName.
     *
     * @param project   the IntelliJ project
     * @param className fully qualified class name
     * @param memberName member name (method, field, or inner class), or null for class only
     * @return the resolve result
     */
    public static ResolveResult resolve(Project project, String className, @Nullable String memberName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass psiClass = findClass(project, className, scope);

        if (psiClass == null) {
            return new ResolveResult.ClassNotFound(className);
        }

        if (memberName == null || memberName.isEmpty()) {
            return new ResolveResult.Success(psiClass, getClassKind(psiClass), psiClass.getName());
        }

        
        // Try to find method first
        PsiMethod[] methods = psiClass.findMethodsByName(memberName, false);
        if (methods.length > 0) {
            return new ResolveResult.Success(methods[0], methods[0].isConstructor() ? "constructor" : "method", memberName);
        }

        // Try to find field
        PsiField field = psiClass.findFieldByName(memberName, false);
        if (field != null) {
            return new ResolveResult.Success(field, "field", memberName);
        }

        // Try to find inner class
        PsiClass innerClass = psiClass.findInnerClassByName(memberName, false);
        if (innerClass != null) {
            return new ResolveResult.Success(innerClass, getClassKind(innerClass), memberName);
        }

        return new ResolveResult.MemberNotFound(memberName, className);
    }

    /**
     * Finds a PsiClass by name, supporting both fully qualified names and simple names.
     *
     * @param project   the IntelliJ project
     * @param className fully qualified or simple class name
     * @param scope     the search scope
     * @return the found PsiClass, or null if not found
     */
    public static PsiClass findClass(Project project, String className, GlobalSearchScope scope) {
        // FQN search
        if (className.contains(".")) {
            PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(className, scope);
            if (classes.length > 0) return classes[0];
        }
        // Short name fallback
        PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope);
        if (classes.length > 0) return classes[0];
        return null;
    }

    static String getClassKind(PsiClass psiClass) {
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
}
