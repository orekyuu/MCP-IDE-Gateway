package net.orekyuu.intellijmcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class ListProjectsToolTest extends BasePlatformTestCase {

    private static final Gson GSON = new Gson();
    private ListProjectsTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new ListProjectsTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("list_projects");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("project");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.required()).isNull();
    }

    public void testExecuteReturnsProjects() {
        McpSchema.CallToolResult result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        String jsonResult = textContent.text();

        JsonArray projects = GSON.fromJson(jsonResult, JsonArray.class);
        assertThat(projects).isNotNull();
        assertThat(projects.size()).isGreaterThanOrEqualTo(1);

        JsonObject project = projects.get(0).getAsJsonObject();
        assertThat(project.has("name")).isTrue();
        assertThat(project.has("locationHash")).isTrue();
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("list_projects");
    }
}
