package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.CommentMessage;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EditInlineCommentTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> ID =
            Arg.string("id", "The thread ID to edit (use list_inline_comments to find IDs)").required();
    private static final Arg<String> COMMENT =
            Arg.string("comment", "The new comment text (supports Markdown)").required();
    private static final Arg<Optional<String>> MESSAGE_ID =
            Arg.string("messageId", "The specific message ID to edit. If omitted, edits the first message.").optional();

    @Override
    public String getDescription() {
        return "Edit the text of a message in an existing inline comment thread. Use list_inline_comments to find the thread ID. If messageId is omitted, edits the first message.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, ID, COMMENT, MESSAGE_ID);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, ID, COMMENT, MESSAGE_ID)
                .mapN((project, id, comment, messageIdOpt) -> {
                    try {
                        InlineCommentService service = InlineCommentService.getInstance(project);

                        // Resolve messageId: use provided or fall back to first message
                        String resolvedMessageId;
                        if (messageIdOpt.isPresent()) {
                            resolvedMessageId = messageIdOpt.get();
                        } else {
                            // Find the thread and get first messageId
                            InlineComment thread = service.getAllComments().stream()
                                    .filter(c -> c.getId().equals(id))
                                    .findFirst()
                                    .orElse(null);
                            if (thread == null) {
                                return errorResult("Error: Comment with id '" + id + "' not found.");
                            }
                            List<CommentMessage> msgs = thread.getMessages();
                            if (msgs.isEmpty()) {
                                return errorResult("Error: Thread has no messages.");
                            }
                            resolvedMessageId = msgs.get(0).getMessageId();
                        }

                        InlineComment updated = service.updateMessage(id, resolvedMessageId, comment);
                        if (updated == null) {
                            return errorResult("Error: Comment with id '" + id + "' not found.");
                        }
                        return successResult(new EditInlineCommentResponse(
                                updated.getId(),
                                updated.getFilePath(),
                                updated.getLine(),
                                resolvedMessageId,
                                comment,
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
            String messageId,
            String comment,
            String message
    ) {}
}
