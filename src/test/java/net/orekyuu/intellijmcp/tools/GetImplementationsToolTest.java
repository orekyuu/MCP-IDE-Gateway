package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetImplementationsToolTest extends BasePlatformTestCase {

    private GetImplementationsTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetImplementationsTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_implementations");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("implementation");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("className")
                .containsKey("projectPath")
                .containsKey("includeAbstract");
        assertThat(schema.required())
                .isNotNull()
                .contains("className", "projectPath");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetImplementationsTool.GetImplementationsResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "SomeClass"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetImplementationsTool.GetImplementationsResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "className", "NonExistentClassName12345",
                "projectPath", "/nonexistent/project/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetImplementationsTool.GetImplementationsResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_implementations");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
