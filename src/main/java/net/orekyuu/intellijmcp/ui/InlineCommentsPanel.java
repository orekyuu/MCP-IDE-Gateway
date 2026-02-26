package net.orekyuu.intellijmcp.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SeparatorWithText;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import net.orekyuu.intellijmcp.comment.CommentMessage;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentRenderer;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

/**
 * Panel displaying inline comments with a grouped list on the left and thread/preview on the right.
 */
public class InlineCommentsPanel implements Disposable {

    private enum SortMode { BY_FILE, BY_LAST_UPDATED }

    private final JPanel mainPanel;
    private final Project project;
    private SortMode sortMode = SortMode.BY_FILE;

    private CollectionListModel<InlineComment> listModel;
    private JBList<InlineComment> commentList;
    private JPanel threadPanel;
    private JPanel previewPanel;

    @Nullable
    private EditorEx previewEditor;

    public InlineCommentsPanel(Project project) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());

        // Splitter: left 25% / right 75%
        JBSplitter splitter = new JBSplitter(false, 0.25f);
        splitter.setFirstComponent(buildLeftPanel());
        splitter.setSecondComponent(buildRightPanel());
        mainPanel.add(splitter, BorderLayout.CENTER);

        // Subscribe to comment changes
        project.getMessageBus().connect(this).subscribe(InlineCommentService.TOPIC,
                new InlineCommentService.InlineCommentListener() {
                    @Override
                    public void onCommentAdded(InlineComment c) {
                        refreshList(null);
                    }

                    @Override
                    public void onCommentRemoved(InlineComment c) {
                        boolean wasSelected = c.equals(commentList.getSelectedValue());
                        refreshList(null);
                        if (wasSelected) updateRightPanel(null);
                    }

                    @Override
                    public void onCommentEdited(InlineComment c) {
                        refreshList(commentList.getSelectedValue());
                        if (c.equals(commentList.getSelectedValue())) showThread(c);
                    }

                    @Override
                    public void onReplyAdded(InlineComment c) {
                        refreshList(commentList.getSelectedValue());
                        if (c.equals(commentList.getSelectedValue())) showThread(c);
                    }

                    @Override
                    public void onAllCommentsCleared() {
                        refreshList(null);
                        updateRightPanel(null);
                    }
                });

        // Load existing comments
        refreshList(null);
    }

    private JComponent buildLeftPanel() {
        // Toolbar (inside left panel so it doesn't affect right panel height)
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ClearAllAction());
        group.add(new SortToggleAction());
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("InlineComments", group, true);

        listModel = new CollectionListModel<>();
        commentList = new JBList<>(listModel);
        toolbar.setTargetComponent(commentList);
        commentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListItemDescriptor<InlineComment> descriptor = new ListItemDescriptor<>() {
            @Override
            public String getTextFor(InlineComment c) {
                String first = c.getFirstMessageText();
                String preview = first.length() > 25 ? first.substring(0, 25) + "..." : first;
                String badge = c.getMessages().isEmpty() ? ""
                        : c.getMessages().get(0).getAuthor() == CommentMessage.Author.AI ? "[AI]" : "[You]";
                if (sortMode == SortMode.BY_LAST_UPDATED) {
                    String fileName = Paths.get(c.getFilePath()).getFileName().toString();
                    return fileName + "  " + badge + "  " + preview;
                }
                return badge + "  " + preview;
            }

            @Override
            public String getCaptionAboveOf(InlineComment c) {
                if (sortMode == SortMode.BY_LAST_UPDATED) return null;
                return Paths.get(c.getFilePath()).getFileName().toString();
            }

            @Override
            public boolean hasSeparatorAboveOf(InlineComment c) {
                if (sortMode == SortMode.BY_LAST_UPDATED) return false;
                int idx = listModel.getElementIndex(c);
                if (idx <= 0) return true;
                return !listModel.getElementAt(idx - 1).getFilePath().equals(c.getFilePath());
            }

            @Override
            public Icon getIconFor(InlineComment c) {
                return null;
            }

            @Override
            public String getTooltipFor(InlineComment c) {
                return c.getFilePath();
            }
        };
        commentList.setCellRenderer(new GroupedItemsListRenderer<>(descriptor) {
            @Override
            protected SeparatorWithText createSeparator() {
                SeparatorWithText sep = super.createSeparator();
                sep.setCaptionCentered(false);
                return sep;
            }
        });

        commentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateRightPanel(commentList.getSelectedValue());
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(commentList), BorderLayout.CENTER);
        return leftPanel;
    }

    private JComponent buildRightPanel() {
        threadPanel = new JPanel(new BorderLayout());
        threadPanel.add(new JLabel("Select a comment", SwingConstants.CENTER), BorderLayout.CENTER);

        previewPanel = new JPanel(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Thread", threadPanel);
        tabs.addTab("File Preview", previewPanel);

        return tabs;
    }

    private List<InlineComment> sortComments(List<InlineComment> all) {
        if (sortMode == SortMode.BY_LAST_UPDATED) {
            return all.stream()
                    .sorted(Comparator.comparing(InlineComment::getUpdatedAt).reversed())
                    .toList();
        }
        return all.stream()
                .sorted(Comparator.comparing(InlineComment::getFilePath)
                        .thenComparingInt(InlineComment::getLine))
                .toList();
    }

    private void showThread(@Nullable InlineComment comment) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(() -> showThread(comment));
            return;
        }
        threadPanel.removeAll();
        if (comment == null) {
            threadPanel.add(new JLabel("Select a comment", SwingConstants.CENTER), BorderLayout.CENTER);
        } else {
            JComponent comp = InlineCommentRenderer.createCommentComponent(
                    project,
                    comment,
                    () -> InlineCommentService.getInstance(project).removeComment(comment.getId()),
                    () -> {
                        showThread(comment);
                        threadPanel.revalidate();
                        threadPanel.repaint();
                    }
            );
            ScrollablePanel wrapper = new ScrollablePanel(comp);
            JScrollPane sp = new JScrollPane(wrapper);
            sp.setBorder(null);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            threadPanel.add(sp, BorderLayout.CENTER);
        }
        threadPanel.revalidate();
        threadPanel.repaint();
    }

    private void showFilePreview(@Nullable InlineComment comment) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(() -> showFilePreview(comment));
            return;
        }
        releasePreviewEditor();
        previewPanel.removeAll();

        if (comment != null) {
            VirtualFile file = VirtualFileManager.getInstance()
                    .findFileByNioPath(Paths.get(comment.getFilePath()));
            Document document = file != null
                    ? FileDocumentManager.getInstance().getDocument(file) : null;

            if (file != null && document != null) {
                EditorEx editor = (EditorEx) EditorFactory.getInstance()
                        .createViewer(document, project, EditorKind.PREVIEW);

                EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
                editor.setHighlighter(EditorHighlighterFactory.getInstance()
                        .createEditorHighlighter(file, scheme, project));

                editor.getSettings().setLineNumbersShown(true);
                editor.getSettings().setFoldingOutlineShown(false);
                editor.getSettings().setLineMarkerAreaShown(false);
                editor.getSettings().setGutterIconsShown(false);
                editor.getSettings().setCaretRowShown(false);

                int line0 = comment.getLine() - 1;
                TextAttributes highlight = new TextAttributes(
                        null,
                        new JBColor(new Color(0xFFFBCC), new Color(0x3B3A2A)),
                        null, null, Font.PLAIN);
                editor.getMarkupModel().addLineHighlighter(line0,
                        HighlighterLayer.SELECTION - 1, highlight);

                ApplicationManager.getApplication().invokeLater(() ->
                        editor.getScrollingModel().scrollTo(
                                new LogicalPosition(line0, 0), ScrollType.CENTER));

                previewEditor = editor;
                previewPanel.add(editor.getComponent(), BorderLayout.CENTER);
            }
        }

        previewPanel.revalidate();
        previewPanel.repaint();
    }

    private void releasePreviewEditor() {
        if (previewEditor != null) {
            EditorFactory.getInstance().releaseEditor(previewEditor);
            previewEditor = null;
        }
    }

    private void updateRightPanel(@Nullable InlineComment comment) {
        showThread(comment);
        showFilePreview(comment);
    }

    private void refreshList(@Nullable InlineComment toSelect) {
        ApplicationManager.getApplication().invokeLater(() -> {
            List<InlineComment> sorted = sortComments(
                    InlineCommentService.getInstance(project).getAllComments());
            listModel.replaceAll(sorted);
            if (toSelect != null) {
                int idx = listModel.getElementIndex(toSelect);
                if (idx >= 0) commentList.setSelectedIndex(idx);
            }
        });
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void dispose() {
        releasePreviewEditor();
    }

    private class ClearAllAction extends com.intellij.openapi.actionSystem.AnAction {
        ClearAllAction() {
            super("Clear All", "Remove all inline comments", AllIcons.Actions.GC);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            InlineCommentService.getInstance(project).clearAll();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private class SortToggleAction extends ToggleAction {
        SortToggleAction() {
            super("Sort by Last Updated", "Toggle sort order: File / Last Updated",
                    AllIcons.ObjectBrowser.SortByType);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return sortMode == SortMode.BY_LAST_UPDATED;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            sortMode = state ? SortMode.BY_LAST_UPDATED : SortMode.BY_FILE;
            refreshList(commentList.getSelectedValue());
            commentList.repaint();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /** JScrollPane 内でコンテンツ幅を viewport に追従させるラッパーパネル。 */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        ScrollablePanel(JComponent content) {
            super(new BorderLayout());
            add(content, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
