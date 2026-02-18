package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AddInlineCommentToolTest extends BaseMcpToolTest<AddInlineCommentTool> {

    @Override
    AddInlineCommentTool createTool() {
        return new AddInlineCommentTool();
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of(
                "line", 1,
                "comment", "test",
                "projectPath", "/some/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithMissingComment() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 1,
                "projectPath", "/some/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("comment");
    }

    @Test
    void executeWithMissingLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "comment", "test",
                "projectPath", "/some/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("line");
    }

    @Test
    void executeWithInvalidLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 0,
                "comment", "test comment",
                "projectPath", "/some/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("line must be >= 1");
    }

    @Test
    void executeWithNegativeLine() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", -5,
                "comment", "test comment",
                "projectPath", "/some/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("line must be >= 1");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 1,
                "comment", "test comment",
                "projectPath", "/nonexistent/project/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

}
