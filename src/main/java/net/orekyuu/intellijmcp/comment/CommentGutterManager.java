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
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Manages the "+" gutter icon that appears when hovering over a line in the editor.
 * Uses a single persistent RangeHighlighter with LineMarkerRendererEx + ActiveGutterRenderer.
 * Position.LEFT is used so paint() receives the left free painters area rectangle.
 */
public class CommentGutterManager implements LineMarkerRendererEx, ActiveGutterRenderer, Disposable {

    private static final int ICON_AREA_WIDTH = 16;

    private final Editor editor;
    private final Project project;
    int hoverLine = -1;
    boolean columnHovered = false;
    private final RangeHighlighter highlighter;

    private final EditorMouseMotionListener mouseMotionListener;
    private final EditorMouseListener mouseListener;

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

        // Reserve left free painters area width for the "+" icon
        if (editor instanceof EditorEx editorEx) {
            EditorGutterComponentEx gutter = editorEx.getGutterComponentEx();
            gutter.reserveLeftFreePaintersAreaWidth(this, ICON_AREA_WIDTH);
        }

        // Attach mouse listeners to the editor for reliable hover detection
        mouseMotionListener = new EditorMouseMotionListener() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent e) {
                int newLine = e.getLogicalPosition().line;
                boolean newColumnHovered = isIconColumnHovered(e);
                if (newLine == hoverLine && newColumnHovered == columnHovered) return;
                int prevLine = hoverLine;
                hoverLine = newLine;
                columnHovered = newColumnHovered;
                repaintGutterLine(prevLine);
                repaintGutterLine(newLine);
            }
        };
        mouseListener = new EditorMouseListener() {
            @Override
            public void mouseExited(@NotNull EditorMouseEvent e) {
                if (hoverLine < 0) return;
                int prevLine = hoverLine;
                hoverLine = -1;
                columnHovered = false;
                repaintGutterLine(prevLine);
            }
        };
        editor.addEditorMouseMotionListener(mouseMotionListener);
        editor.addEditorMouseListener(mouseListener);
    }

    // --- LineMarkerRendererEx ---

    @Override
    public @NotNull Position getPosition() {
        return Position.LEFT;
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

        // Paint at the left edge of the LEFT painters area
        int iconX = r.x;
        AllIcons.General.InlineAdd.paintIcon(null, g, iconX, iconY);
    }

    // --- ActiveGutterRenderer ---

    @Override
    public boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
        if (!columnHovered) return false;
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
        editor.removeEditorMouseMotionListener(mouseMotionListener);
        editor.removeEditorMouseListener(mouseListener);
        if (highlighter.isValid()) {
            editor.getMarkupModel().removeHighlighter(highlighter);
        }
    }

    private boolean isIconColumnHovered(EditorMouseEvent e) {
        if (!(editor instanceof EditorEx)) return false;
        EditorGutterComponentEx gutter = ((EditorEx) editor).getGutterComponentEx();
        if (e.getMouseEvent().getComponent() != gutter) return false;
        // getLeftFreePaintersAreaOffset() is private in EditorGutterComponentImpl but
        // its implementation returns getLineMarkerAreaOffset() — use that public equivalent.
        int iconStart = gutter.getLineMarkerAreaOffset();
        int iconEnd = iconStart + JBUI.scale(ICON_AREA_WIDTH);
        int mouseX = e.getMouseEvent().getX();
        return mouseX >= iconStart && mouseX <= iconEnd;
    }

    private void repaintGutterLine(int line) {
        if (line < 0) return;
        if (!(editor instanceof EditorEx)) return;
        EditorGutterComponentEx gutter = ((EditorEx) editor).getGutterComponentEx();
        gutter.repaint();
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
