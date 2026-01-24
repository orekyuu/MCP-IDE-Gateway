package net.orekyuu.intellijmcp.listeners;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import net.orekyuu.intellijmcp.services.McpServerService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class McpServerStartupListener implements AppLifecycleListener {
    private static final Logger LOG = Logger.getInstance(McpServerStartupListener.class);

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        LOG.info("Application frame created, starting MCP Server...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                McpServerService service = McpServerService.getInstance();
                service.startServer();
                LOG.info("MCP Server startup initiated successfully");
            } catch (Exception e) {
                LOG.error("Failed to start MCP Server on application startup", e);
            }
        });
    }
}
