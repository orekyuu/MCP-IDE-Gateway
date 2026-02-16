package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class CreateFileOrDirectoryToolTest extends BasePlatformTestCase {

    private CreateFileOrDirectoryTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new CreateFileOrDirectoryTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("create_file_or_directory");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("create");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("projectPath")
                .containsKey("path")
                .containsKey("isDirectory");
        assertThat(schema.required())
                .isNotNull()
                .contains("projectPath", "path", "isDirectory");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("path", "src/Foo.java", "isDirectory", false));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, CreateFileOrDirectoryTool.CreateResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithMissingPath() {
        var result = tool.execute(Map.of("projectPath", "/some/path", "isDirectory", false));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, CreateFileOrDirectoryTool.CreateResponse>) result;
        assertThat(errorResult.message().message()).contains("path");
    }

    public void testExecuteWithMissingIsDirectory() {
        var result = tool.execute(Map.of("projectPath", "/some/path", "path", "src/Foo.java"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, CreateFileOrDirectoryTool.CreateResponse>) result;
        assertThat(errorResult.message().message()).contains("isDirectory");
    }

    public void testExecuteWithPathOutsideProject() {
        var args = new HashMap<String, Object>();
        args.put("projectPath", "/some/path");
        args.put("path", "../../etc/passwd");
        args.put("isDirectory", false);

        var result = tool.execute(args);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, CreateFileOrDirectoryTool.CreateResponse>) result;
        assertThat(errorResult.message().message()).contains("outside the project directory");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("create_file_or_directory");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
