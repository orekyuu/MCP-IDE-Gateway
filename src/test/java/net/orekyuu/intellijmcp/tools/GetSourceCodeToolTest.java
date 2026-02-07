package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetSourceCodeToolTest extends BasePlatformTestCase {

    private GetSourceCodeTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetSourceCodeTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_source_code");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("source code");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("projectPath")
                .containsKey("className")
                .containsKey("memberName");
        assertThat(schema.required())
                .isNotNull()
                .contains("projectPath", "className")
                .doesNotContain("memberName");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "com.example.MyClass"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetSourceCodeTool.GetSourceCodeResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetSourceCodeTool.GetSourceCodeResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "className", "com.example.MyClass"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetSourceCodeTool.GetSourceCodeResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_source_code");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
