package net.orekyuu.intellijmcp.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

@Service
public final class McpServerService implements Disposable {
    private static final Logger LOG = Logger.getInstance(McpServerService.class);

    private McpServerImpl mcpServer;
    private Thread serverThread;
    private volatile boolean running = false;

    public static McpServerService getInstance() {
        return ApplicationManager.getApplication().getService(McpServerService.class);
    }

    public synchronized void startServer() {
        if (running) {
            LOG.warn("MCP Server is already running");
            logService().warn("MCP Server is already running");
            return;
        }

        LOG.info("Starting MCP Server...");
        logService().info("Starting MCP Server...");

        try {
            mcpServer = new McpServerImpl();

            serverThread = new Thread(() -> {
                try {
                    running = true;
                    mcpServer.start();
                } catch (Exception e) {
                    LOG.error("MCP Server encountered an error", e);
                    logService().error("MCP Server encountered an error: " + e.getMessage());
                    running = false;
                }
            }, "MCP-Server-Thread");

            serverThread.setDaemon(true);
            serverThread.start();

            LOG.info("MCP Server started successfully");
            logService().info("MCP Server started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start MCP Server", e);
            logService().error("Failed to start MCP Server: " + e.getMessage());
            running = false;
        }
    }

    private McpServerLogService logService() {
        return McpServerLogService.getInstance();
    }

    public synchronized void stopServer() {
        if (!running) {
            LOG.info("MCP Server is not running");
            logService().info("MCP Server is not running");
            return;
        }

        LOG.info("Stopping MCP Server...");
        logService().info("Stopping MCP Server...");

        try {
            running = false;

            if (mcpServer != null) {
                mcpServer.stop();
            }

            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
                serverThread.join(5000);
            }

            LOG.info("MCP Server stopped successfully");
            logService().info("MCP Server stopped successfully");
        } catch (Exception e) {
            LOG.error("Error while stopping MCP Server", e);
            logService().error("Error while stopping MCP Server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void dispose() {
        stopServer();
    }
}
