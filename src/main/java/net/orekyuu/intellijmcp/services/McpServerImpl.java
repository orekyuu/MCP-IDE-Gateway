package net.orekyuu.intellijmcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.settings.McpServerSettings;
import net.orekyuu.intellijmcp.tools.McpToolBean;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP Server implementation using MCP Java SDK 0.12.1 with SSE over HTTP.
 * Provides tools for IntelliJ IDEA project and file operations.
 * Runs on embedded Jetty server for HTTP/SSE communication.
 */
public class McpServerImpl {
    private static final Logger LOG = Logger.getInstance(McpServerImpl.class);

    private McpSyncServer mcpServer;
    private Server jettyServer;
    private Thread shutdownHook;
    private int port;

    public void start() throws IOException {
        port = McpServerSettings.getInstance().getPort();
        LOG.info("Initializing MCP Server with HTTP/SSE transport on port " + port + "...");
        logService().info("Initializing MCP Server with HTTP/SSE transport on port " + port + "...");

        try {
            // Create transport provider
            ObjectMapper objectMapper = new ObjectMapper();
            HttpServletSseServerTransportProvider transportProvider =
                    HttpServletSseServerTransportProvider.builder()
                        .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                        .messageEndpoint("/mcp/message")
                        .keepAliveInterval(Duration.ofSeconds(30))
                        .build();

            // Build MCP server
            var jsonMapper = new JacksonMcpJsonMapper(objectMapper);
            var jsonSchemaValidator = new DefaultJsonSchemaValidator(objectMapper);
            mcpServer = McpServer.sync(transportProvider)
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(jsonSchemaValidator)
                .serverInfo("mcp-ide-gateway", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .logging()
                        .prompts(false)
                        .resources(false, false)
                        .tools(true)
                        .build())
                .build();

            // Register tools
            registerTools(mcpServer);

            // Start Jetty HTTP server
            startJettyServer(transportProvider);

            // Add shutdown hook
            shutdownHook = new Thread(() -> {
                LOG.info("Shutting down MCP Server...");
                stop();
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            LOG.info("MCP Server initialized and started successfully");
            LOG.info("HTTP/SSE endpoint: http://localhost:" + port);
            LOG.info("SSE endpoint: http://localhost:" + port + "/sse");
            LOG.info("Message endpoint: http://localhost:" + port + "/mcp/message");
            LOG.info("MCP tools registered via Extension Point");

            logService().info("MCP Server initialized and started successfully");
            logService().info("HTTP/SSE endpoint: http://localhost:" + port);
            logService().info("SSE endpoint: http://localhost:" + port + "/sse");
            logService().info("Message endpoint: http://localhost:" + port + "/mcp/message");

        } catch (Exception e) {
            LOG.error("Failed to initialize MCP Server", e);
            logService().error("Failed to initialize MCP Server: " + e.getMessage());
            throw new IOException("Failed to initialize MCP Server", e);
        }
    }

    private McpServerLogService logService() {
        return McpServerLogService.getInstance();
    }

    private void startJettyServer(HttpServletSseServerTransportProvider transportProvider) throws Exception {
        // Create Jetty server
        jettyServer = new Server(port);

        // Create servlet context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Add MCP transport as servlet
        // HttpServletSseServerTransportProvider extends HttpServlet
        ServletHolder servletHolder = new ServletHolder(transportProvider);
        context.addServlet(servletHolder, "/*");

        // Set handler and start the server
        jettyServer.setHandler(context);
        jettyServer.start();

        LOG.info("Jetty HTTP server started on port " + port);
        logService().info("Jetty HTTP server started on port " + port);
    }


    private void registerTools(McpSyncServer server) {
        List<McpToolBean> beans = McpToolBean.EP_NAME.getExtensionList();
        List<String> toolNames = new ArrayList<>();
        for (McpToolBean bean : beans) {
            server.addTool(bean.toSpecification());
            toolNames.add(bean.name);
        }
        LOG.info("Registered MCP tools: " + String.join(", ", toolNames));
        logService().info("Registered " + beans.size() + " MCP tools");
    }

    public void stop() {
        LOG.info("Stopping MCP Server...");
        logService().info("Stopping MCP Server...");
        try {
            // Stop Jetty server
            if (jettyServer != null && jettyServer.isStarted()) {
                jettyServer.stop();
                LOG.info("Jetty HTTP server stopped");
                logService().info("Jetty HTTP server stopped");
            }

            // Close MCP server
            if (mcpServer != null) {
                mcpServer.close();
            }

            // Remove shutdown hook
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    // Already shutting down, ignore
                }
            }

            LOG.info("MCP Server stopped successfully");
            logService().info("MCP Server stopped successfully");
        } catch (Exception e) {
            LOG.error("Error while stopping MCP Server", e);
            logService().error("Error while stopping MCP Server: " + e.getMessage());
        }
    }
}
