package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiDocumentManager;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    // Inspired by analysis.kt: refresh VFS, commit documents, and wait for indexing to be smart
    try {
      LocalFileSystem lfs = LocalFileSystem.getInstance();

      // Optionally mark suspicious files dirty (best-effort; internal API)
      if (lfs instanceof LocalFileSystemImpl) {
        ((LocalFileSystemImpl) lfs).markSuspiciousFilesDirty(java.util.Collections.emptyList());
      }

      // Collect project directory and content roots
      Set<VirtualFile> filesToRefresh = new LinkedHashSet<>();
      String basePath = project.getBasePath();
      if (basePath != null) {
        Path nio = Paths.get(basePath);
        VirtualFile projectDir = lfs.refreshAndFindFileByNioFile(nio);
        if (projectDir != null) {
          filesToRefresh.add(projectDir);
        }
      }
      for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
        if (root != null) filesToRefresh.add(root);
      }

      // Refresh collected files synchronously (wait via latch)
      if (!filesToRefresh.isEmpty()) {
        CountDownLatch refreshDone = new CountDownLatch(1);
        lfs.refreshFiles(new ArrayList<>(filesToRefresh), true, true, refreshDone::countDown);
        // Wait with a generous timeout to avoid indefinite blocking in tests
        refreshDone.await(30, TimeUnit.SECONDS);
      }

      // Commit all PSI documents under write action
      ApplicationManager.getApplication().invokeAndWait(() ->
        WriteAction.run(() -> PsiDocumentManager.getInstance(project).commitAllDocuments())
      );

      // Wait until indexing is finished (smart mode)
      CountDownLatch smart = new CountDownLatch(1);
      DumbService.getInstance(project).runWhenSmart(smart::countDown);
      smart.await(30, TimeUnit.SECONDS);

    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } catch (ProcessCanceledException pce) {
      throw pce; // must be rethrown per platform contract
    } catch (Throwable ignored) {
      // Best-effort sync; ignore other exceptions to avoid breaking tool execution
    }
  }
}
