package net.orekyuu.intellijmcp.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
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
    }
}
