package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import io.modelcontextprotocol.server.McpSyncServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for MCP tools.
 * Manages tool registration and provides batch registration to MCP server.
 */
public class McpToolRegistry {

    private static final Logger LOG = Logger.getInstance(McpToolRegistry.class);

    private final List<McpTool<?>> tools = new ArrayList<>();

    /**
     * Creates a new registry with all default tools registered.
     */
    public static McpToolRegistry createDefault() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new ListProjectsTool());
        registry.register(new OpenFileTool());
        registry.register(new CallHierarchyTool());
        registry.register(new FindClassTool());
        registry.register(new GetClassStructureTool());
        registry.register(new GetDefinitionTool());
        registry.register(new FindUsagesTool());
        registry.register(new GetImplementationsTool());
        registry.register(new GetDiagnosticsTool());
        registry.register(new SearchSymbolTool());
        registry.register(new GetTypeHierarchyTool());
        registry.register(new GetDocumentationTool());
        registry.register(new OptimizeImportsTool());
        registry.register(new RenameSymbolTool());
        registry.register(new ExtractMethodTool());
        registry.register(new RunInspectionTool());
        registry.register(new SearchTextTool());
        registry.register(new FindFileTool());
        registry.register(new GetSourceCodeTool());
        registry.register(new ReadFileTool());
        registry.register(new AddInlineCommentTool());
        return registry;
    }

    /**
     * Registers a tool with this registry.
     *
     * @param tool the tool to register
     */
    public void register(McpTool<?> tool) {
        tools.add(tool);
    }

    /**
     * Returns an unmodifiable list of all registered tools.
     */
    public List<McpTool<?>> getTools() {
        return Collections.unmodifiableList(tools);
    }

    /**
     * Returns the number of registered tools.
     */
    public int size() {
        return tools.size();
    }

    /**
     * Registers all tools with the given MCP server.
     *
     * @param server the MCP server to register tools with
     */
    public void registerAllWithServer(McpSyncServer server) {
        LOG.info("Registering " + tools.size() + " MCP tools...");

        List<String> toolNames = new ArrayList<>();
        for (McpTool<?> tool : tools) {
            server.addTool(tool.toSpecification());
            toolNames.add(tool.getName());
        }

        LOG.info("Registered MCP tools: " + String.join(", ", toolNames));
    }
}
