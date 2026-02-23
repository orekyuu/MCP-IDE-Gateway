package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Small helper to build project/module structures inside light fixture tests.
 */
final class TestProjectBuilder {

    private TestProjectBuilder() {}

    static void removeModules(@NotNull Project project, @NotNull List<Module> modulesToRemove) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
            try {
                for (Module module : modulesToRemove) {
                    if (module.isDisposed()) {
                        continue;
                    }
                    if (model.findModuleByName(module.getName()) != null) {
                        model.disposeModule(module);
                    }
                }
                model.commit();
            } catch (Throwable t) {
                model.dispose();
                throw t;
            }
        });
    }

    static @NotNull Module createModule(@NotNull Project project,
                                        @NotNull String moduleName,
                                        @NotNull String relativeDir) {
        VirtualFile dir = createDir(project, relativeDir);
        return WriteCommandAction.writeCommandAction(project).compute(() -> {
            ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
            Module module;
            try {
                String moduleFilePath = dir.getPath() + "/" + moduleName + ".iml";
                module = model.newModule(moduleFilePath, JavaModuleType.getModuleType().getId());
                model.commit();
            } catch (Throwable t) {
                model.dispose();
                throw t;
            }
            ModuleRootModificationUtil.updateModel(module, mod -> mod.addContentEntry(dir));
            return module;
        });
    }

    static void addSourceFolder(@NotNull Project project,
                                @NotNull Module module,
                                @NotNull String relativePath) {
        VirtualFile dir = createDir(project, relativePath);
        PsiTestUtil.addSourceRoot(module, dir, JavaSourceRootType.SOURCE);
    }

    static void addTestSourceFolder(@NotNull Project project,
                                    @NotNull Module module,
                                    @NotNull String relativePath) {
        VirtualFile dir = createDir(project, relativePath);
        PsiTestUtil.addSourceRoot(module, dir, JavaSourceRootType.TEST_SOURCE);
    }

    static void addResourceFolder(@NotNull Project project,
                                  @NotNull Module module,
                                  @NotNull String relativePath) {
        VirtualFile dir = createDir(project, relativePath);
        PsiTestUtil.addResourceContentToRoots(module, dir, false);
    }

    static void addTestResourceFolder(@NotNull Project project,
                                      @NotNull Module module,
                                      @NotNull String relativePath) {
        VirtualFile dir = createDir(project, relativePath);
        PsiTestUtil.addResourceContentToRoots(module, dir, true);
    }

    static @NotNull Library addProjectLibrary(@NotNull Project project,
                                              @NotNull Module module,
                                              @NotNull String libraryName,
                                              @NotNull String relativePath) {
        VirtualFile dir = createDir(project, relativePath);
        return PsiTestUtil.addProjectLibrary(module, libraryName, List.of(dir), List.of());
    }

    private static VirtualFile createDir(@NotNull Project project, @NotNull String relativePath) {
        try {
            Path basePath = Path.of(Objects.requireNonNull(project.getBasePath()));
            Path dirPath = basePath.resolve(relativePath);
            Files.createDirectories(dirPath);
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dirPath);
            if (file == null) {
                throw new IllegalStateException("Failed to create directory: " + dirPath);
            }
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
