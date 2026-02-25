package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.Map;

public class EditInlineCommentTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> ID =
            Arg.string("id", "The comment ID to edit (use list_inline_comments to find IDs)").required();
    private static final Arg<String> COMMENT =
            Arg.string("comment", "The new comment text (supports Markdown)").required();

    @Override
    public String getDescription() {
        return "Edit the text of an existing inline comment in the IDE editor. Use list_inline_comments to find the comment ID. The file and line remain unchanged.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, ID, COMMENT);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, ID, COMMENT)
                .mapN((project, id, comment) -> {
                    try {
                        InlineCommentService service = InlineCommentService.getInstance(project);
                        InlineComment updated = service.updateComment(id, comment);
                        if (updated == null) {
                            return errorResult("Error: Comment with id '" + id + "' not found.");
                        }
                        return successResult(new EditInlineCommentResponse(
                                updated.getId(),
                                updated.getFilePath(),
                                updated.getLine(),
                                updated.getComment(),
                                "Inline comment updated successfully"
                        ));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record EditInlineCommentResponse(
            String commentId,
            String filePath,
            int line,
            String comment,
            String message
    ) {}
}
