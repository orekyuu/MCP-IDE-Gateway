package net.orekyuu.intellijmcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.McpToolRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.time.Duration;

/**
 * MCP Server implementation using MCP Java SDK 0.12.1 with SSE over HTTP.
 * Provides tools for IntelliJ IDEA project and file operations.
 * Runs on embedded Jetty server for HTTP/SSE communication.
 */
public class McpServerImpl {
    private static final Logger LOG = Logger.getInstance(McpServerImpl.class);
    private static final int DEFAULT_PORT = 3000;

    private McpSyncServer mcpServer;
    private Server jettyServer;
    private Thread shutdownHook;

    public void start() throws IOException {
        LOG.info("Initializing MCP Server with HTTP/SSE transport...");

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
                .serverInfo("intellij-mcp", "1.0.0")
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
            LOG.info("HTTP/SSE endpoint: http://localhost:" + DEFAULT_PORT);
            LOG.info("SSE endpoint: http://localhost:" + DEFAULT_PORT + "/sse");
            LOG.info("Message endpoint: http://localhost:" + DEFAULT_PORT + "/mcp/message");
            LOG.info("MCP tools registered via McpToolRegistry");

        } catch (Exception e) {
            LOG.error("Failed to initialize MCP Server", e);
            throw new IOException("Failed to initialize MCP Server", e);
        }
    }

    private void startJettyServer(HttpServletSseServerTransportProvider transportProvider) throws Exception {
        // Create Jetty server
        jettyServer = new Server(DEFAULT_PORT);

        // Create servlet context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Add MCP transport as servlet
        // HttpServletSseServerTransportProvider extends HttpServlet
        ServletHolder servletHolder = new ServletHolder(transportProvider);
        context.addServlet(servletHolder, "/*");

        // Start the server
        jettyServer.start();

        LOG.info("Jetty HTTP server started on port " + DEFAULT_PORT);
    }


    private void registerTools(McpSyncServer server) {
        McpToolRegistry registry = McpToolRegistry.createDefault();
        registry.registerAllWithServer(server);
    }

    public void stop() {
        LOG.info("Stopping MCP Server...");
        try {
            // Stop Jetty server
            if (jettyServer != null && jettyServer.isStarted()) {
                jettyServer.stop();
                LOG.info("Jetty HTTP server stopped");
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
        } catch (Exception e) {
            LOG.error("Error while stopping MCP Server", e);
        }
    }
}
