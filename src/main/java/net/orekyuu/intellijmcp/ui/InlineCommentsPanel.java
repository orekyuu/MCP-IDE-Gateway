package net.orekyuu.intellijmcp.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.table.JBTable;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying a table of inline comments with jump-to-source and clear-all functionality.
 */
public class InlineCommentsPanel implements Disposable {

    private final JPanel mainPanel;
    private final Project project;
    private final JBTable table;
    private final CommentTableModel tableModel;

    public InlineCommentsPanel(Project project) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());

        // Table model
        tableModel = new CommentTableModel();
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-click to jump
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < tableModel.comments.size()) {
                        jumpToComment(tableModel.comments.get(row));
                    }
                }
            }
        });

        // Toolbar
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new ClearAllAction());
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("InlineComments", actionGroup, false);
        toolbar.setTargetComponent(mainPanel);

        // Layout
        mainPanel.add(toolbar.getComponent(), BorderLayout.WEST);
        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Subscribe to comment changes
        project.getMessageBus().connect(this).subscribe(InlineCommentService.TOPIC,
                new InlineCommentService.InlineCommentListener() {
                    @Override
                    public void onCommentAdded(InlineComment comment) {
                        refreshTable();
                    }

                    @Override
                    public void onCommentRemoved(InlineComment comment) {
                        refreshTable();
                    }

                    @Override
                    public void onCommentEdited(InlineComment comment) {
                        refreshTable();
                    }

                    @Override
                    public void onReplyAdded(InlineComment comment) {
                        refreshTable();
                    }

                    @Override
                    public void onAllCommentsCleared() {
                        refreshTable();
                    }
                });

        // Load existing comments
        refreshTable();
    }

    private void refreshTable() {
        ApplicationManager.getApplication().invokeLater(() -> {
            tableModel.comments = InlineCommentService.getInstance(project).getAllComments();
            tableModel.fireTableDataChanged();
        });
    }

    private void jumpToComment(InlineComment comment) {
        VirtualFile file = VirtualFileManager.getInstance()
                .findFileByNioPath(Paths.get(comment.getFilePath()));
        if (file != null) {
            int line = Math.max(0, comment.getLine() - 1);
            new OpenFileDescriptor(project, file, line, 0).navigate(true);
        }
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void dispose() {
        // Connection is disposed automatically since we passed 'this' as parent
    }

    private static class CommentTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"File", "Line", "Comment"};
        private List<InlineComment> comments = new ArrayList<>();

        @Override
        public int getRowCount() {
            return comments.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InlineComment comment = comments.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> {
                    Path path = Paths.get(comment.getFilePath());
                    yield path.getFileName().toString();
                }
                case 1 -> comment.getLine();
                case 2 -> {
                    String text = comment.getFirstMessageText();
                    yield text.length() > 100 ? text.substring(0, 100) + "..." : text;
                }
                default -> "";
            };
        }
    }

    private class ClearAllAction extends AnAction {
        ClearAllAction() {
            super("Clear All", "Remove all inline comments", com.intellij.icons.AllIcons.Actions.GC);
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
}
