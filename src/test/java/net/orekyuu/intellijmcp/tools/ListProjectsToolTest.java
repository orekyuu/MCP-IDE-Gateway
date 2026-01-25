package net.orekyuu.intellijmcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class ListProjectsToolTest extends BasePlatformTestCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
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

    public void testExecuteReturnsProjects() throws Exception {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);

        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, ListProjectsTool.ListProjectsResponse>) result;
        ListProjectsTool.ListProjectsResponse response = successResult.message();

        assertThat(response.projects()).isNotNull();
        assertThat(response.projects().size()).isGreaterThanOrEqualTo(1);

        ListProjectsTool.ListProjectsResponse.ProjectInfo project = response.projects().get(0);
        assertThat(project.name()).isNotNull();
        assertThat(project.locationHash()).isNotNull();
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("list_projects");
    }
}
