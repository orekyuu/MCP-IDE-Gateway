package net.orekyuu.intellijmcp.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the MCP Server tool window.
 */
public class McpServerToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create Server Log tab
        ServerLogPanel serverLogPanel = new ServerLogPanel();
        Content logContent = ContentFactory.getInstance().createContent(
                serverLogPanel.getComponent(),
                "Server Log",
                false
        );
        toolWindow.getContentManager().addContent(logContent);

        // Create Inline Comments tab
        InlineCommentsPanel commentsPanel = new InlineCommentsPanel(project);
        Content commentsContent = ContentFactory.getInstance().createContent(
                commentsPanel.getComponent(),
                "Inline Comments",
                false
        );
        Disposer.register(commentsContent, commentsPanel);
        toolWindow.getContentManager().addContent(commentsContent);
    }
}
