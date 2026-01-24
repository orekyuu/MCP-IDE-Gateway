package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public class OpenFileToolTest extends BasePlatformTestCase {

    private OpenFileTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new OpenFileTool();
    }

    public void testGetName() {
        assertEquals("open_file", tool.getName());
    }

    public void testGetDescription() {
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().toLowerCase().contains("open"));
        assertTrue(tool.getDescription().toLowerCase().contains("file"));
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertTrue(schema.properties().containsKey("filePath"));
        assertTrue(schema.properties().containsKey("projectName"));

        // filePath should be required
        assertNotNull(schema.required());
        assertTrue(schema.required().contains("filePath"));
        assertFalse(schema.required().contains("projectName"));
    }

    public void testExecuteWithMissingFilePath() {
        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertNotNull(result);
        assertTrue(result.isError());

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertTrue(textContent.text().contains("filePath"));
    }

    public void testExecuteWithNonExistentFile() {
        McpSchema.CallToolResult result = tool.execute(Map.of(
                "filePath", "/nonexistent/path/to/file.java"
        ));

        assertNotNull(result);
        assertTrue(result.isError());

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertTrue(textContent.text().contains("not found"));
    }

    public void testExecuteWithInvalidProjectName() {
        // Use a path that doesn't need VFS access validation
        McpSchema.CallToolResult result = tool.execute(Map.of(
                "filePath", "/some/test/file.java",
                "projectName", "NonExistentProject"
        ));

        assertNotNull(result);
        assertTrue(result.isError());

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertTrue(textContent.text().contains("Project not found"));
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("open_file", spec.tool().name());
        assertNotNull(spec.tool().inputSchema());
    }
}
