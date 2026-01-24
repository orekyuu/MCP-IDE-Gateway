package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * Interface for MCP tools.
 * Each tool provides a name, description, input schema, and execution logic.
 */
public interface McpTool {

    /**
     * Returns the unique name of the tool.
     * This name is used to identify the tool in MCP protocol.
     */
    String getName();

    /**
     * Returns a human-readable description of what the tool does.
     */
    String getDescription();

    /**
     * Returns the JSON schema that defines the input parameters for this tool.
     */
    McpSchema.JsonSchema getInputSchema();

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the input arguments as a map
     * @return the result of the tool execution
     */
    McpSchema.CallToolResult execute(Map<String, Object> arguments);

    /**
     * Converts this tool to an MCP SDK SyncToolSpecification.
     * Default implementation builds the specification from interface methods.
     */
    default McpServerFeatures.SyncToolSpecification toSpecification() {
        var tool = McpSchema.Tool.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema(getInputSchema())
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, arguments) -> execute(arguments)
        );
    }
}
