package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ListInlineCommentsTool extends AbstractProjectMcpTool<Object> {

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Optional<ProjectRelativePath>> FILE_PATH =
            Arg.optionalProjectRelativePath("filePath", "Relative path to filter comments by file. If omitted, returns all inline comments in the project.");

    @Override
    public String getDescription() {
        return "List all inline comments displayed in the IDE editor. Optionally filter by file path. Returns comment id, file path, line number, and comment text.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, FILE_PATH);
    }

    @Override
    public Result<ErrorResponse, Object> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, FILE_PATH)
                .mapN((project, filePath) -> {
                    try {
                        InlineCommentService service = InlineCommentService.getInstance(project);
                        List<InlineComment> comments;
                        if (filePath.isPresent()) {
                            String absolutePath = filePath.get().resolve(project).toString();
                            comments = service.getCommentsForFile(absolutePath);
                        } else {
                            comments = service.getAllComments();
                        }

                        List<InlineCommentInfo> infos = comments.stream()
                                .map(c -> new InlineCommentInfo(c.getId(), c.getFilePath(), c.getLine(), c.getComment()))
                                .toList();

                        return successResult(new ListInlineCommentsResponse(infos));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    // --- Response records ---

    public record ListInlineCommentsResponse(
            List<InlineCommentInfo> comments
    ) {}

    public record InlineCommentInfo(
            String id,
            String filePath,
            int line,
            String comment
    ) {}
}
