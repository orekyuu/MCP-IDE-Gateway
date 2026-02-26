package net.orekyuu.intellijmcp.comment;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.LineMarkerRendererEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Manages the "+" gutter icon that appears when hovering over a line in the editor.
 * Uses a single persistent RangeHighlighter with LineMarkerRendererEx + ActiveGutterRenderer.
 * Position.CUSTOM is used so paint() receives the full gutter width regardless of left-area state.
 */
public class CommentGutterManager implements LineMarkerRendererEx, ActiveGutterRenderer, Disposable {

    private final Editor editor;
    private final Project project;
    private int hoverLine = -1;
    private final RangeHighlighter highlighter;

    public CommentGutterManager(Editor editor, Project project) {
        this.editor = editor;
        this.project = project;

        // Single persistent highlighter covering the entire document
        highlighter = editor.getMarkupModel().addRangeHighlighter(
                null, 0, Math.max(0, editor.getDocument().getTextLength() - 1),
                HighlighterLayer.LAST,
                HighlighterTargetArea.LINES_IN_RANGE
        );
        highlighter.setGreedyToRight(true);
        highlighter.setLineMarkerRenderer(this);

        // Attach mouse listeners directly to the gutter component for reliable hover detection
        if (editor instanceof EditorEx editorEx) {
            EditorGutterComponentEx gutter = editorEx.getGutterComponentEx();
            gutter.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int newLine = editorEx.xyToLogicalPosition(new Point(0, e.getY())).line;
                    if (newLine == hoverLine) return;
                    hoverLine = newLine;
                    gutter.repaint();
                }
            });
            gutter.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverLine < 0) return;
                    hoverLine = -1;
                    gutter.repaint();
                }
            });
        }
    }

    // --- LineMarkerRendererEx ---

    @Override
    public @NotNull Position getPosition() {
        return Position.CUSTOM;
    }

    // --- LineMarkerRenderer (paint) ---

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
        if (hoverLine < 0) return;
        if (hoverLine >= editor.getDocument().getLineCount()) return;

        int iconSize = JBUI.scale(16);
        // Compute Y using visualLineToY for consistency with gutter coordinate system
        int visualLine = editor.logicalToVisualPosition(new LogicalPosition(hoverLine, 0)).line;
        int lineY = editor.visualLineToY(visualLine);
        int iconY = lineY + (editor.getLineHeight() - iconSize) / 2;

        // Paint at the left edge of the gutter (left free painters area)
        int iconX = r.x + JBUI.scale(1);
        AllIcons.General.InlineAdd.paintIcon(null, g, iconX, iconY);
    }

    // --- ActiveGutterRenderer ---

    @Override
    public boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
        if (hoverLine < 0) return false;
        int iconSize = JBUI.scale(16);
        int visualLine = editor.logicalToVisualPosition(new LogicalPosition(hoverLine, 0)).line;
        int lineY = editor.visualLineToY(visualLine);
        int iconY = lineY + (editor.getLineHeight() - iconSize) / 2;
        return e.getY() >= iconY && e.getY() <= iconY + iconSize;
    }

    @Override
    public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
        if (hoverLine < 0) return;
        e.consume(); // Prevent MyMouseAdapter from also firing (which would toggle breakpoints)
        showCommentDialog(hoverLine + 1); // 0-based → 1-based
    }

    @Override
    public @NotNull String getAccessibleName() {
        return "Add inline comment";
    }

    @Override
    public void dispose() {
        if (highlighter.isValid()) {
            editor.getMarkupModel().removeHighlighter(highlighter);
        }
    }

    private void showCommentDialog(int lineNumber) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file == null) return;
        NewCommentDialog dialog = new NewCommentDialog(project, lineNumber);
        if (dialog.showAndGet()) {
            String text = dialog.getCommentText();
            if (!text.isBlank()) {
                InlineCommentService.getInstance(project)
                        .addComment(file.getPath(), lineNumber, text, CommentMessage.Author.USER);
            }
        }
    }
}
