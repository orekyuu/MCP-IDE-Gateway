package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class OpenFileToolTest extends BasePlatformTestCase {

    private OpenFileTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new OpenFileTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("open_file");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("open")
                .containsIgnoringCase("file");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, OpenFileTool.OpenFileResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithNonExistentFile() {
        var result = tool.execute(Map.of(
                "filePath", "/nonexistent/path/to/file.java",
                "projectPath", "/some/project"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, OpenFileTool.OpenFileResponse>) result;
        assertThat(errorResult.message().message()).containsIgnoringCase("not found");
    }

    public void testExecuteWithInvalidProjectPath() {
        var result = tool.execute(Map.of(
                "filePath", "/some/test/file.java",
                "projectPath", "/nonexistent/project/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, OpenFileTool.OpenFileResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("open_file");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
