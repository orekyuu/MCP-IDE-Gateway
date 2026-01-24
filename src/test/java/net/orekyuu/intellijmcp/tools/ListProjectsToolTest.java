package net.orekyuu.intellijmcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public class ListProjectsToolTest extends BasePlatformTestCase {

    private static final Gson GSON = new Gson();
    private ListProjectsTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new ListProjectsTool();
    }

    public void testGetName() {
        assertEquals("list_projects", tool.getName());
    }

    public void testGetDescription() {
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("project"));
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
        // list_projects has no required parameters
        assertNull(schema.required());
    }

    public void testExecuteReturnsProjects() {
        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertNotNull(result);
        assertFalse(result.isError());
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());

        // Parse the result content
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        String jsonResult = textContent.text();

        // Should be valid JSON array
        JsonArray projects = GSON.fromJson(jsonResult, JsonArray.class);
        assertNotNull(projects);

        // In test environment, there should be at least one project (the test project)
        assertTrue(projects.size() >= 1);

        // Check project structure
        JsonObject project = projects.get(0).getAsJsonObject();
        assertTrue(project.has("name"));
        assertTrue(project.has("locationHash"));
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("list_projects", spec.tool().name());
    }
}
