package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.ComponentInlayAlignment;
import com.intellij.openapi.editor.ComponentInlayKt;
import com.intellij.openapi.editor.ComponentInlayRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project-level service that manages inlay rendering of inline comments in editors.
 * Uses ComponentInlayRenderer to embed real Swing components as block inlays.
 */
@Service(Service.Level.PROJECT)
public final class InlineCommentEditorListener implements Disposable {

    private final Project project;
    private final Map<String, Inlay<?>> commentInlays = new ConcurrentHashMap<>();
    private final Map<Editor, CommentGutterManager> gutterManagers = new ConcurrentHashMap<>();

    public InlineCommentEditorListener(Project project) {
        this.project = project;

        // Listen for editor open/close
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                if (isIrrelevantEditor(editor)) return;

                // Register gutter manager for "+" hover icon
                gutterManagers.put(editor, new CommentGutterManager(editor, project));

                String filePath = getFilePath(editor);
                if (filePath == null) return;

                // Add existing comments for this file
                List<InlineComment> comments = InlineCommentService.getInstance(project)
                        .getCommentsForFile(filePath);
                for (InlineComment comment : comments) {
                    addInlayForComment(editor, comment);
                }
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                if (isIrrelevantEditor(editor)) return;

                // Dispose gutter manager
                CommentGutterManager gm = gutterManagers.remove(editor);
                if (gm != null) Disposer.dispose(gm);

                String filePath = getFilePath(editor);
                if (filePath == null) return;

                // Remove inlays for this editor's file
                List<InlineComment> comments = InlineCommentService.getInstance(project)
                        .getCommentsForFile(filePath);
                for (InlineComment comment : comments) {
                    Inlay<?> inlay = commentInlays.remove(comment.getId());
                    if (inlay != null && inlay.isValid()) {
                        Disposer.dispose(inlay);
                    }
                }
            }
        }, this);

        // Listen for comment changes
        project.getMessageBus().connect(this).subscribe(InlineCommentService.TOPIC,
                new InlineCommentService.InlineCommentListener() {
                    @Override
                    public void onCommentAdded(InlineComment comment) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                                if (isIrrelevantEditor(editor)) continue;
                                String filePath = getFilePath(editor);
                                if (comment.getFilePath().equals(filePath)) {
                                    addInlayForComment(editor, comment);
                                }
                            }
                        });
                    }

                    @Override
                    public void onCommentRemoved(InlineComment comment) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            Inlay<?> inlay = commentInlays.remove(comment.getId());
                            if (inlay != null && inlay.isValid()) {
                                Disposer.dispose(inlay);
                            }
                        });
                    }

                    @Override
                    public void onCommentEdited(InlineComment comment) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                refreshInlayForComment(comment));
                    }

                    @Override
                    public void onReplyAdded(InlineComment comment) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                refreshInlayForComment(comment));
                    }

                    @Override
                    public void onAllCommentsCleared() {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            for (Inlay<?> inlay : commentInlays.values()) {
                                if (inlay.isValid()) {
                                    Disposer.dispose(inlay);
                                }
                            }
                            commentInlays.clear();
                        });
                    }
                });

        // Register gutter managers for editors already open at startup
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (!isIrrelevantEditor(editor)) {
                gutterManagers.put(editor, new CommentGutterManager(editor, project));
            }
        }
    }

    public static InlineCommentEditorListener getInstance(Project project) {
        return project.getService(InlineCommentEditorListener.class);
    }

    private boolean isIrrelevantEditor(Editor editor) {
        Project editorProject = editor.getProject();
        return editorProject == null || !editorProject.equals(project);
    }

    private String getFilePath(Editor editor) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        return file != null ? file.getPath() : null;
    }

    private void refreshInlayForComment(InlineComment comment) {
        Inlay<?> oldInlay = commentInlays.remove(comment.getId());
        if (oldInlay != null && oldInlay.isValid()) {
            Disposer.dispose(oldInlay);
        }
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (isIrrelevantEditor(editor)) continue;
            if (comment.getFilePath().equals(getFilePath(editor))) {
                addInlayForComment(editor, comment);
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void addInlayForComment(Editor editor, InlineComment comment) {
        if (commentInlays.containsKey(comment.getId())) return;

        int lineNumber = comment.getLine() - 1; // Convert to 0-based
        if (lineNumber < 0 || lineNumber >= editor.getDocument().getLineCount()) return;

        int offset = editor.getDocument().getLineEndOffset(lineNumber);

        JComponent component = InlineCommentRenderer.createCommentComponent(
                editor,
                comment,
                () -> InlineCommentService.getInstance(project).removeComment(comment.getId()),
                () -> refreshInlayForComment(comment)
        );

        InlayProperties properties = new InlayProperties()
                .relatesToPrecedingText(true)
                .showAbove(false)
                .priority(0);

        ComponentInlayRenderer<JComponent> renderer =
                new ComponentInlayRenderer<>(component, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH);
        Inlay<?> inlay = ComponentInlayKt.addComponentInlay(
                editor, offset, properties, renderer
        );

        if (inlay != null) {
            commentInlays.put(comment.getId(), inlay);
            editor.getContentComponent().revalidate();
            editor.getContentComponent().repaint();
        }
    }

    @Override
    public void dispose() {
        for (Inlay<?> inlay : commentInlays.values()) {
            if (inlay.isValid()) {
                Disposer.dispose(inlay);
            }
        }
        commentInlays.clear();

        for (CommentGutterManager gm : gutterManagers.values()) {
            Disposer.dispose(gm);
        }
        gutterManagers.clear();
    }
}
