package net.orekyuu.intellijmcp.ui;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import net.orekyuu.intellijmcp.services.McpServerLogService;
import net.orekyuu.intellijmcp.services.McpServerService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Panel that displays MCP server logs in a console view.
 */
public class ServerLogPanel implements Disposable {

    private final JPanel mainPanel;
    private final ConsoleView consoleView;
    private final MessageBusConnection connection;

    public ServerLogPanel() {
        mainPanel = new JPanel(new BorderLayout());

        // Create console view - use the default project for console builder
        var project = ProjectManager.getInstance().getDefaultProject();
        consoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .getConsole();

        // Add toolbar
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new StartServerAction());
        actionGroup.add(new StopServerAction());
        actionGroup.add(new RestartServerAction());
        actionGroup.addSeparator();
        actionGroup.add(new ClearLogAction());
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("McpServerLog", actionGroup, false);
        toolbar.setTargetComponent(mainPanel);

        // Layout
        mainPanel.add(toolbar.getComponent(), BorderLayout.WEST);
        mainPanel.add(consoleView.getComponent(), BorderLayout.CENTER);

        // Subscribe to log events
        connection = ApplicationManager.getApplication().getMessageBus()
                .connect(this);
        connection.subscribe(McpServerLogService.LOG_TOPIC, new McpServerLogService.LogListener() {
            @Override
            public void onLogEntry(McpServerLogService.LogEntry entry) {
                appendLog(entry);
            }

            @Override
            public void onLogCleared() {
                consoleView.clear();
            }
        });

        // Load existing logs
        loadExistingLogs();

        Disposer.register(this, consoleView);
    }

    private void loadExistingLogs() {
        McpServerLogService logService = McpServerLogService.getInstance();
        for (McpServerLogService.LogEntry entry : logService.getLogEntries()) {
            appendLog(entry);
        }
    }

    private void appendLog(McpServerLogService.LogEntry entry) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ConsoleViewContentType contentType = switch (entry.level()) {
                case ERROR -> ConsoleViewContentType.ERROR_OUTPUT;
                case WARN -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
                case DEBUG -> ConsoleViewContentType.LOG_DEBUG_OUTPUT;
                default -> ConsoleViewContentType.NORMAL_OUTPUT;
            };
            consoleView.print(entry.format() + "\n", contentType);
        });
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void dispose() {
        // Connection is disposed automatically since we passed 'this' as parent
    }

    private class StartServerAction extends AnAction {
        StartServerAction() {
            super("Start Server", "Start MCP server", com.intellij.icons.AllIcons.Actions.Execute);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            McpServerService.getInstance().startServer();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!McpServerService.getInstance().isRunning());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private class StopServerAction extends AnAction {
        StopServerAction() {
            super("Stop Server", "Stop MCP server", com.intellij.icons.AllIcons.Actions.Suspend);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            McpServerService.getInstance().stopServer();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(McpServerService.getInstance().isRunning());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private class RestartServerAction extends AnAction {
        RestartServerAction() {
            super("Restart Server", "Restart MCP server", com.intellij.icons.AllIcons.Actions.Restart);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            McpServerService service = McpServerService.getInstance();
            service.stopServer();
            service.startServer();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(McpServerService.getInstance().isRunning());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private class ClearLogAction extends AnAction {
        ClearLogAction() {
            super("Clear Log", "Clear all log entries", com.intellij.icons.AllIcons.Actions.GC);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            McpServerLogService.getInstance().clear();
        }
    }
}
