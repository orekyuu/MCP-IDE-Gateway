package net.orekyuu.intellijmcp.tools;

import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class EditInlineCommentToolTest extends BaseMcpToolTest<EditInlineCommentTool> {

    @Override
    EditInlineCommentTool createTool() {
        return new EditInlineCommentTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("id", "some-id", "comment", "new text"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingId() {
        var result = tool.execute(Map.of("projectPath", "/some/project", "comment", "new text"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("id");
    }

    @Test
    void executeWithMissingComment() {
        var result = tool.execute(Map.of("projectPath", "/some/project", "id", "some-id"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("comment");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "id", "some-id",
                "comment", "new text"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithNonExistentId() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "id", "nonexistent-id-12345",
                "comment", "new text"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("not found");
    }

    @Test
    void executeUpdatesCommentText() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        InlineCommentService service = InlineCommentService.getInstance(getProject());
        service.clearAll();
        InlineComment added = service.addComment("/some/file.java", 10, "original text");

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "id", added.getId(),
                "comment", "updated text"
        ));
        McpToolResultAssert.assertThat(result).isSuccess();

        InlineComment updated = service.getAllComments().getFirst();
        assertThat(updated.getId()).isEqualTo(added.getId());
        assertThat(updated.getFirstMessageText()).isEqualTo("updated text");
        assertThat(updated.getFilePath()).isEqualTo("/some/file.java");
        assertThat(updated.getLine()).isEqualTo(10);
    }
}
