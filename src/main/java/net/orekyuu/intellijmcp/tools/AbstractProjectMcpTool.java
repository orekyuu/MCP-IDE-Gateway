package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.PsiDocumentManager;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractProjectMcpTool<R> extends AbstractMcpTool<R> {

    @Override
    public final Result<ErrorResponse, R> execute(Map<String, Object> arguments) {
        Args.validate(arguments, Arg.project()).mapN(project -> {
            syncProjectFiles(project);
            return Void.class;
        });
        return doExecute(arguments);
    }

    abstract Result<ErrorResponse, R> doExecute(Map<String, Object> arguments);

    private void syncProjectFiles(Project project) {
        var dumbService = DumbService.getInstance(project);
        var localFileSystem = LocalFileSystem.getInstance();
        var contentRoots = Arrays.stream(ProjectRootManager.getInstance(project).getContentRoots()).collect(Collectors.toSet());

        if (project.getBasePath() == null) {
            return;
        }
        var projectDirVirtualFile = localFileSystem.refreshAndFindFileByNioFile(Paths.get(project.getBasePath()));

        if (localFileSystem instanceof LocalFileSystemImpl impl) {
            impl.markSuspiciousFilesDirty(List.of());
        }

        var dirtyFiles = Stream.concat(Stream.of(projectDirVirtualFile), contentRoots.stream()).filter(VirtualFile::isDirectory).filter(virtualFile -> {
            if (virtualFile instanceof VirtualFileSystemEntry e) {
                return e.isDirty();
            }
            return false;
        }).toList();

        ApplicationManager.getApplication().runWriteAction(() -> {
            LocalFileSystem.getInstance().refreshFiles(dirtyFiles, false, true, null);
            PsiDocumentManager.getInstance(project).commitAllDocuments();
        });

        if (!ApplicationManager.getApplication().isDispatchThread()) {
            dumbService.waitForSmartMode();
        }
    }
}