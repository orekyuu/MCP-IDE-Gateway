package net.orekyuu.intellijmcp.tools;

import net.orekyuu.intellijmcp.comment.InlineCommentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ListInlineCommentsToolTest extends BaseMcpToolTest<ListInlineCommentsTool> {

    @Override
    ListInlineCommentsTool createTool() {
        return new ListInlineCommentsTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of());
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of("projectPath", "/nonexistent/project/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithPathTraversal() {
        var result = tool.execute(Map.of(
                "projectPath", "/some/project",
                "filePath", "../../etc/passwd"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Path is outside the project directory");
    }

    @Test
    void executeReturnsAllComments() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        InlineCommentService service = InlineCommentService.getInstance(getProject());
        service.clearAll();
        service.addComment("/some/file.java", 10, "comment A");
        service.addComment("/other/file.java", 20, "comment B");

        var result = tool.execute(Map.of("projectPath", projectPath));
        McpToolResultAssert.assertThat(result).isSuccess();

        var response = (ListInlineCommentsTool.ListInlineCommentsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.comments()).hasSize(2);
    }

    @Test
    void executeFiltersByFilePath() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        InlineCommentService service = InlineCommentService.getInstance(getProject());
        service.clearAll();
        service.addComment(projectPath + "/src/A.java", 1, "comment for A");
        service.addComment(projectPath + "/src/B.java", 2, "comment for B");

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "src/A.java"
        ));
        McpToolResultAssert.assertThat(result).isSuccess();

        var response = (ListInlineCommentsTool.ListInlineCommentsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.comments()).hasSize(1);
        assertThat(response.comments().getFirst().comment()).isEqualTo("comment for A");
    }

    @Test
    void executeReturnsEmptyWhenNoComments() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        InlineCommentService.getInstance(getProject()).clearAll();

        var result = tool.execute(Map.of("projectPath", projectPath));
        McpToolResultAssert.assertThat(result).isSuccess();

        var response = (ListInlineCommentsTool.ListInlineCommentsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.comments()).isEmpty();
    }
}
