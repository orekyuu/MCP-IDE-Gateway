package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaBuilderTest {

    @Test
    void buildEmptySchema() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object().build();

        assertEquals("object", schema.type());
        assertNull(schema.properties());
        assertNull(schema.required());
    }

    @Test
    void buildSchemaWithRequiredString() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredString("filePath", "Path to the file")
                .build();

        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertEquals(1, schema.properties().size());

        @SuppressWarnings("unchecked")
        Map<String, Object> filePathProp = (Map<String, Object>) schema.properties().get("filePath");
        assertEquals("string", filePathProp.get("type"));
        assertEquals("Path to the file", filePathProp.get("description"));

        assertNotNull(schema.required());
        assertEquals(List.of("filePath"), schema.required());
    }

    @Test
    void buildSchemaWithOptionalString() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalString("projectName", "Name of the project")
                .build();

        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertEquals(1, schema.properties().size());

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("projectName");
        assertEquals("string", prop.get("type"));
        assertEquals("Name of the project", prop.get("description"));

        // Optional should not be in required list
        assertNull(schema.required());
    }

    @Test
    void buildSchemaWithRequiredInteger() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredInteger("offset", "Character offset")
                .build();

        assertEquals("object", schema.type());
        assertNotNull(schema.properties());

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("offset");
        assertEquals("integer", prop.get("type"));
        assertEquals("Character offset", prop.get("description"));

        assertEquals(List.of("offset"), schema.required());
    }

    @Test
    void buildSchemaWithOptionalInteger() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalInteger("depth", "Search depth")
                .build();

        assertNotNull(schema.properties());

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("depth");
        assertEquals("integer", prop.get("type"));

        assertNull(schema.required());
    }

    @Test
    void buildSchemaWithRequiredBoolean() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredBoolean("recursive", "Search recursively")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("recursive");
        assertEquals("boolean", prop.get("type"));

        assertEquals(List.of("recursive"), schema.required());
    }

    @Test
    void buildSchemaWithOptionalBoolean() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .optionalBoolean("verbose", "Verbose output")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> prop = (Map<String, Object>) schema.properties().get("verbose");
        assertEquals("boolean", prop.get("type"));

        assertNull(schema.required());
    }

    @Test
    void buildSchemaWithMultipleProperties() {
        McpSchema.JsonSchema schema = JsonSchemaBuilder.object()
                .requiredString("filePath", "Path to the file")
                .requiredInteger("offset", "Character offset")
                .optionalString("projectName", "Name of the project")
                .optionalInteger("depth", "Search depth")
                .build();

        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertEquals(4, schema.properties().size());

        // Only required fields should be in required list
        assertNotNull(schema.required());
        assertEquals(2, schema.required().size());
        assertTrue(schema.required().contains("filePath"));
        assertTrue(schema.required().contains("offset"));
        assertFalse(schema.required().contains("projectName"));
        assertFalse(schema.required().contains("depth"));
    }

    @Test
    void builderIsFluent() {
        JsonSchemaBuilder builder = JsonSchemaBuilder.object();

        // All methods should return the same builder instance
        assertSame(builder, builder.requiredString("a", "desc"));
        assertSame(builder, builder.optionalString("b", "desc"));
        assertSame(builder, builder.requiredInteger("c", "desc"));
        assertSame(builder, builder.optionalInteger("d", "desc"));
        assertSame(builder, builder.requiredBoolean("e", "desc"));
        assertSame(builder, builder.optionalBoolean("f", "desc"));
    }
}
