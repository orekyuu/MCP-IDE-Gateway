package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JsonSchemaBuilderTest {

    @Test
    void buildEmptySchema() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object().build();

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).isNull();
        assertThat(schema.required()).isNull();
    }

    @Test
    void buildSchemaWithRequiredString() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredString("filePath", "Path to the file")
                .build();

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> filePathProp = (Map<String, Object>) schema.properties().get("filePath");
        assertThat(filePathProp)
                .containsEntry("type", "string")
                .containsEntry("description", "Path to the file");

        assertThat(schema.required()).containsExactly("filePath");
    }

    @Test
    void buildSchemaWithOptionalString() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalString("projectName", "Name of the project")
                .build();

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("projectName");
        assertThat(prop)
                .containsEntry("type", "string")
                .containsEntry("description", "Name of the project");

        assertThat(schema.required()).isNull();
    }

    @Test
    void buildSchemaWithRequiredInteger() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredInteger("offset", "Character offset")
                .build();

        assertThat(schema.type()).isEqualTo("object");

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("offset");
        assertThat(prop)
                .containsEntry("type", "integer")
                .containsEntry("description", "Character offset");

        assertThat(schema.required()).containsExactly("offset");
    }

    @Test
    void buildSchemaWithOptionalInteger() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalInteger("depth", "Search depth")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("depth");
        assertThat(prop).containsEntry("type", "integer");

        assertThat(schema.required()).isNull();
    }

    @Test
    void buildSchemaWithRequiredBoolean() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredBoolean("recursive", "Search recursively")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("recursive");
        assertThat(prop).containsEntry("type", "boolean");

        assertThat(schema.required()).containsExactly("recursive");
    }

    @Test
    void buildSchemaWithOptionalBoolean() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalBoolean("verbose", "Verbose output")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("verbose");
        assertThat(prop).containsEntry("type", "boolean");

        assertThat(schema.required()).isNull();
    }

    @Test
    void buildSchemaWithMultipleProperties() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredString("filePath", "Path to the file")
                .requiredInteger("offset", "Character offset")
                .optionalString("projectName", "Name of the project")
                .optionalInteger("depth", "Search depth")
                .build();

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).hasSize(4);
        assertThat(schema.required())
                .hasSize(2)
                .contains("filePath", "offset")
                .doesNotContain("projectName", "depth");
    }

    @Test
    void buildSchemaWithOptionalStringArray() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalStringArray("names", "List of names")
                .build();

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("names");
        assertThat(prop)
                .containsEntry("type", "array")
                .containsEntry("description", "List of names");

        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) prop.get("items");
        assertThat(items).containsEntry("type", "string");

        assertThat(schema.required()).isNull();
    }

    @Test
    void builderIsFluent() {
        JsonSchemaBuilder builder = JsonSchemaBuilder.object();

        assertThat(builder.requiredString("a", "desc")).isSameAs(builder);
        assertThat(builder.optionalString("b", "desc")).isSameAs(builder);
        assertThat(builder.requiredInteger("c", "desc")).isSameAs(builder);
        assertThat(builder.optionalInteger("d", "desc")).isSameAs(builder);
        assertThat(builder.requiredBoolean("e", "desc")).isSameAs(builder);
        assertThat(builder.optionalBoolean("f", "desc")).isSameAs(builder);
        assertThat(builder.optionalStringArray("g", "desc")).isSameAs(builder);
    }
}
