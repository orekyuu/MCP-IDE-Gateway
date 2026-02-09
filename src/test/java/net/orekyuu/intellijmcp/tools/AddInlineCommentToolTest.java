package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class AddInlineCommentToolTest extends BasePlatformTestCase {

    private AddInlineCommentTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new AddInlineCommentTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("add_inline_comment");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("inline comment");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("line")
                .containsKey("comment")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "line", "comment", "projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of(
                "line", 1,
                "comment", "test",
                "projectPath", "/some/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithMissingComment() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 1,
                "projectPath", "/some/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("comment");
    }

    public void testExecuteWithMissingLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "comment", "test",
                "projectPath", "/some/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("line");
    }

    public void testExecuteWithInvalidLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 0,
                "comment", "test comment",
                "projectPath", "/some/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("line must be >= 1");
    }

    public void testExecuteWithNegativeLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", -5,
                "comment", "test comment",
                "projectPath", "/some/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("line must be >= 1");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 1,
                "comment", "test comment",
                "projectPath", "/nonexistent/project/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, AddInlineCommentTool.AddInlineCommentResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("add_inline_comment");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
