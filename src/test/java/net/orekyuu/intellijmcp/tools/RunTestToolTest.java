package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class RunTestToolTest extends BasePlatformTestCase {

    private RunTestTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new RunTestTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("run_test");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("test");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("projectPath")
                .containsKey("filePath")
                .containsKey("methodName")
                .containsKey("configurationName")
                .containsKey("timeoutSeconds");
        assertThat(schema.required())
                .isNotNull()
                .contains("projectPath", "filePath")
                .doesNotContain("methodName", "configurationName", "timeoutSeconds");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("filePath", "src/test/Foo.java"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/project"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("run_test");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
