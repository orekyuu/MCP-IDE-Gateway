package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for entering a new inline comment thread.
 */
public class NewCommentDialog extends DialogWrapper {

    private final EditorTextField textField;
    private final int line;

    public NewCommentDialog(@Nullable Project project, int line) {
        super(project);
        this.line = line;
        textField = new EditorTextField("", project, PlainTextFileType.INSTANCE);
        textField.setOneLineMode(false);
        textField.setPreferredSize(new Dimension(480, 120));
        setTitle("Add Inline Comment");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel label = new JLabel("Comment for line " + line + "  (Markdown supported)");
        panel.add(label, BorderLayout.NORTH);
        panel.add(textField, BorderLayout.CENTER);

        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return textField;
    }

    public String getCommentText() {
        return textField.getText().trim();
    }
}
