package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.Map;

public class RemoveInlineCommentTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<String> ID =
            Arg.string("id", "The comment ID to remove (use list_inline_comments to find IDs)").required();

    @Override
    public String getDescription() {
        return "Remove an inline comment from the IDE editor by its ID. Use list_inline_comments to find the comment ID.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, ID);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, ID)
                .mapN((project, id) -> {
                    try {
                        InlineCommentService service = InlineCommentService.getInstance(project);
                        boolean existed = service.getAllComments().stream()
                                .anyMatch(c -> c.getId().equals(id));
                        if (!existed) {
                            return errorResult("Error: Comment with id '" + id + "' not found.");
                        }
                        service.removeComment(id);
                        return successResult(new RemoveInlineCommentResponse(id, "Inline comment removed successfully"));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record RemoveInlineCommentResponse(
            String commentId,
            String message
    ) {}
}
