package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.comment.InlineComment;
import net.orekyuu.intellijmcp.comment.InlineCommentService;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that adds an inline comment to a file in the IDE editor.
 * The comment is displayed as a block inlay above the specified line.
 */
public class AddInlineCommentTool extends AbstractMcpTool<AddInlineCommentTool.AddInlineCommentResponse> {

    @Override
    public String getName() {
        return "add_inline_comment";
    }

    @Override
    public String getDescription() {
        return "Add an inline comment to a file in the IDE editor. The comment is displayed as a block inlay above the specified line. Supports Markdown formatting.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file to add a comment to")
                .requiredInteger("line", "Line number to add the comment at (1-based)")
                .requiredString("comment", "The comment text to display (supports Markdown)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, AddInlineCommentResponse> execute(Map<String, Object> arguments) {
        try {
            String filePath;
            String projectPath;
            String comment;
            int line;

            try {
                filePath = getRequiredStringArg(arguments, "filePath");
                projectPath = getRequiredStringArg(arguments, "projectPath");
                comment = getRequiredStringArg(arguments, "comment");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            Optional<Integer> lineOpt = getIntegerArg(arguments, "line");
            if (lineOpt.isEmpty()) {
                return errorResult("Error: line is required");
            }
            line = lineOpt.get();

            if (line < 1) {
                return errorResult("Error: line must be >= 1, got: " + line);
            }

            // Find target project
            Optional<Project> targetProject = findProjectByPath(projectPath);
            if (targetProject.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }

            // Check file exists
            VirtualFile file = runReadAction(() ->
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath))
            );
            if (file == null) {
                return errorResult("Error: File not found: " + filePath);
            }

            // Add the comment
            Project project = targetProject.get();
            InlineComment inlineComment = InlineCommentService.getInstance(project)
                    .addComment(filePath, line, comment);

            return successResult(new AddInlineCommentResponse(
                    inlineComment.getId(),
                    filePath,
                    line,
                    comment,
                    "Inline comment added successfully"
            ));
        } catch (Exception e) {
            return errorResult("Error: " + e.getMessage());
        }
    }

    public record AddInlineCommentResponse(
            String commentId,
            String filePath,
            int line,
            String comment,
            String message
    ) {}
}
