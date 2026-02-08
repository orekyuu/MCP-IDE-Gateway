package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetDefinitionToolTest extends BasePlatformTestCase {

    private GetDefinitionTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetDefinitionTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_definition");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("definition");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("className")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("className", "projectPath");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetDefinitionTool.GetDefinitionResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "com.example.MyClass"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetDefinitionTool.GetDefinitionResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "className", "com.example.MyClass",
                "projectPath", "/nonexistent/project/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetDefinitionTool.GetDefinitionResponse>) result;
        assertThat(errorResult.message().message()).containsIgnoringCase("not found");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_definition");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
