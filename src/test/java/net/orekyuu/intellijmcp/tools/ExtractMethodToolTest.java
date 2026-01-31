package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class ExtractMethodToolTest extends BasePlatformTestCase {

    private ExtractMethodTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new ExtractMethodTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("extract_method");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("extract");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("startLine")
                .containsKey("endLine")
                .containsKey("methodName")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "startLine", "endLine", "methodName", "projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, ExtractMethodTool.ExtractMethodResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "startLine", 1,
                "endLine", 5,
                "methodName", "newMethod"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, ExtractMethodTool.ExtractMethodResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("extract_method");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
