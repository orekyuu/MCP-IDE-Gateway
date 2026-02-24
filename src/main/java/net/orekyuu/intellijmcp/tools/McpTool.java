package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * Interface for MCP tools.
 * Each tool provides a description, input schema, and execution logic.
 * Tool name is declared in plugin.xml via the mcpTool extension point.
 */
public interface McpTool<RESPONSE> {

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

    @SuppressWarnings("unused") // L and R are used in subtype declarations
    sealed interface Result<L, R> {
      record ErrorResponse<L, R>(L message) implements Result<L, R> {}
      record SuccessResponse<L, R>(R message) implements Result<L, R> {}

      static <L, R> ErrorResponse<L, R> error(L message) {
        return new ErrorResponse<>(message);
      }

      static <L, R> SuccessResponse<L, R> success(R message) {
        return new SuccessResponse<>(message);
      }
    }
}
