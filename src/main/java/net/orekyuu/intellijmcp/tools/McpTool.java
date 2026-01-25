package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * Interface for MCP tools.
 * Each tool provides a name, description, input schema, and execution logic.
 */
public interface McpTool<RESPONSE> {

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
    Result<ErrorResponse, RESPONSE> execute(Map<String, Object> arguments);

    public sealed interface Result<L, R> {
      record ErrorResponse<L, R>(L message) implements Result<L, R> {}
      record SuccessResponse<L, R>(R message) implements Result<L, R> {}

      static <L, R> ErrorResponse<L, R> error(L message) {
        return new ErrorResponse<>(message);
      }

      static <L, R> SuccessResponse<L, R> success(R message) {
        return new SuccessResponse<>(message);
      }
    }

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
                (exchange, arguments) -> {
                    Result<ErrorResponse, RESPONSE> result = execute(arguments);
                    return switch (result) {
                        case Result.ErrorResponse<ErrorResponse, RESPONSE> err ->
                                new McpSchema.CallToolResult(
                                        List.of(new McpSchema.TextContent(err.message().message())),
                                        true
                                );
                        case Result.SuccessResponse<ErrorResponse, RESPONSE> success ->
                                new McpSchema.CallToolResult(
                                        List.of(new McpSchema.TextContent(ResponseSerializer.serialize(success.message()))),
                                        false
                                );
                    };
                }
        );
    }
}
