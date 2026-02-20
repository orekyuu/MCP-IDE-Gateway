package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;

/**
 * MCP tool that adds an inline comment to a file in the IDE editor.
 * The comment is displayed as a block inlay below the specified line.
 */
public class AddInlineCommentTool extends AbstractProjectMcpTool<AddInlineCommentTool.AddInlineCommentResponse> {

    private static final Arg<ProjectRelativePath> FILE_PATH =
            Arg.projectRelativePath("filePath", "Relative path to the file to add a comment to");
    private static final Arg<Integer> LINE =
            Arg.integer("line", "Line number to add the comment at (1-based)").min(1).required();
    private static final Arg<String> COMMENT =
            Arg.string("comment", "The comment text to display (supports Markdown)").required();
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getName() {
        return "add_inline_comment";
    }

    @Override
    public String getDescription() {
        return "Add an inline comment to a file in the IDE editor. The comment is displayed as a block inlay below the specified line. Supports Markdown formatting.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, LINE, COMMENT, PROJECT);
    }

    @Override
    public Result<ErrorResponse, AddInlineCommentResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, LINE, COMMENT, PROJECT)
                .mapN((filePath, line, comment, project) -> {
                    try {
                        Path resolvedPath = filePath.resolve(project);
                        String absolutePath = resolvedPath.toString();

                        // Check file exists
                        VirtualFile file = runReadAction(() ->
                                VirtualFileManager.getInstance().findFileByNioPath(resolvedPath)
                        );
                        if (file == null) {
                            return errorResult("Error: File not found: " + absolutePath);
                        }

                        // Add the comment
                        InlineComment inlineComment = InlineCommentService.getInstance(project)
                                .addComment(absolutePath, line, comment);

                        return successResult(new AddInlineCommentResponse(
                                inlineComment.getId(),
                                absolutePath,
                                line,
                                comment,
                                "Inline comment added successfully"
                        ));
                    } catch (Exception e) {
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record AddInlineCommentResponse(
            String commentId,
            String filePath,
            int line,
            String comment,
            String message
    ) {}
}
