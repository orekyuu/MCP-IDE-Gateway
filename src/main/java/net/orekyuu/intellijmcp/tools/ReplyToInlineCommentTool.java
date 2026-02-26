package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.CommentMessage;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.Map;

/**
 * MCP tool that adds an AI reply to an existing inline comment thread.
 */
public class ReplyToInlineCommentTool extends AbstractProjectMcpTool<ReplyToInlineCommentTool.ReplyResponse> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> ID =
            Arg.string("id", "The thread ID to reply to (use list_inline_comments to find IDs)").required();
    private static final Arg<String> COMMENT =
            Arg.string("comment", "The reply text (supports Markdown)").required();

    @Override
    public String getDescription() {
        return "Add an AI reply to an existing inline comment thread. Use list_inline_comments to find the thread ID.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, ID, COMMENT);
    }

    @Override
    public Result<ErrorResponse, ReplyResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, ID, COMMENT)
                .mapN((project, id, comment) -> {
                    try {
                        InlineCommentService service = InlineCommentService.getInstance(project);
                        InlineComment updated = service.addReply(id, CommentMessage.Author.AI, comment);
                        if (updated == null) {
                            return errorResult("Error: Comment thread with id '" + id + "' not found.");
                        }
                        // Get the last message (the one we just added)
                        var messages = updated.getMessages();
                        var addedMessage = messages.get(messages.size() - 1);
                        return successResult(new ReplyResponse(
                                updated.getId(),
                                addedMessage.getMessageId(),
                                updated.getFilePath(),
                                updated.getLine(),
                                comment,
                                "Reply added successfully"
                        ));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record ReplyResponse(
            String commentId,
            String messageId,
            String filePath,
            int line,
            String reply,
            String message
    ) {}
}
