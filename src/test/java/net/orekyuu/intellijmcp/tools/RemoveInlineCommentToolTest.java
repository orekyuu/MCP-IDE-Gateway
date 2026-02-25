package net.orekyuu.intellijmcp.tools;

import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveInlineCommentToolTest extends BaseMcpToolTest<RemoveInlineCommentTool> {

    @Override
    RemoveInlineCommentTool createTool() {
        return new RemoveInlineCommentTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("id", "some-id"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingId() {
        var result = tool.execute(Map.of("projectPath", "/some/project"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("id");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "id", "some-id"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithNonExistentId() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "id", "nonexistent-id-12345"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("not found");
    }

    @Test
    void executeRemovesComment() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        InlineCommentService service = InlineCommentService.getInstance(getProject());
        service.clearAll();
        InlineComment added = service.addComment("/some/file.java", 5, "to be removed");

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "id", added.getId()
        ));
        McpToolResultAssert.assertThat(result).isSuccess();
        assertThat(service.getAllComments()).isEmpty();
    }
}
