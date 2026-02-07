package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for creating McpSchema.JsonSchema instances.
 * Simplifies the creation of JSON schemas for MCP tool input parameters.
 *
 * <p>Example usage:
 * <pre>{@code
 * McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
 *     .requiredString("filePath", "Absolute path to the file")
 *     .optionalString("projectName", "Name of the project")
 *     .build();
 * }</pre>
 */
public class JsonSchemaBuilder {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    private JsonSchemaBuilder() {
    }

    /**
     * Creates a new builder for an object schema.
     */
    public static JsonSchemaBuilder object() {
        return new JsonSchemaBuilder();
    }

    /**
     * Adds a required string property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder requiredString(String name, String description) {
        properties.put(name, Map.of(
                "type", "string",
                "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Adds an optional string property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder optionalString(String name, String description) {
        properties.put(name, Map.of(
                "type", "string",
                "description", description
        ));
        return this;
    }

    /**
     * Adds a required integer property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder requiredInteger(String name, String description) {
        properties.put(name, Map.of(
                "type", "integer",
                "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Adds an optional integer property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder optionalInteger(String name, String description) {
        properties.put(name, Map.of(
                "type", "integer",
                "description", description
        ));
        return this;
    }

    /**
     * Adds a required boolean property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder requiredBoolean(String name, String description) {
        properties.put(name, Map.of(
                "type", "boolean",
                "description", description
        ));
        required.add(name);
        return this;
    }

    /**
     * Adds an optional boolean property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder optionalBoolean(String name, String description) {
        properties.put(name, Map.of(
                "type", "boolean",
                "description", description
        ));
        return this;
    }

    /**
     * Adds an optional string array property.
     *
     * @param name        the property name
     * @param description the property description
     * @return this builder for chaining
     */
    public JsonSchemaBuilder optionalStringArray(String name, String description) {
        properties.put(name, Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", description
        ));
        return this;
    }

    /**
     * Builds the JsonSchema instance.
     *
     * @return the built JsonSchema
     */
    public McpSchema.JsonSchema build() {
        return new McpSchema.JsonSchema(
                "object",
                properties.isEmpty() ? null : properties,
                required.isEmpty() ? null : required,
                null,
                null,
                null
        );
    }
}
