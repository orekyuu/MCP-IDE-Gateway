package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool that reads the content of a file by its path relative to the project root.
 * Supports optional line range specification (1-based).
 */
public class ReadFileTool extends AbstractProjectMcpTool<ReadFileTool.ReadFileResponse> {

    private static final Logger LOG = Logger.getInstance(ReadFileTool.class);

    private static final Arg<ProjectRelativePath> FILE_PATH =
            Arg.projectRelativePath("filePath", "Relative path from the project root to the file to read");
    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Optional<Integer>> START_LINE =
            Arg.integer("startLine", "Start line number (1-based, inclusive). If not specified, reads from the beginning.").min(1).optional();
    private static final Arg<Optional<Integer>> END_LINE =
            Arg.integer("endLine", "End line number (1-based, inclusive). If not specified, reads to the end.").min(1).optional();

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Read the content of a file by its path relative to the project root. Supports optional line range.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, PROJECT, START_LINE, END_LINE);
    }

    @Override
    public Result<ErrorResponse, ReadFileResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, PROJECT, START_LINE, END_LINE)
                .mapN((filePath, project, startLineOpt, endLineOpt) -> {
                    try {
                        Path resolvedPath = filePath.resolve(project);

                        return runReadActionWithResult(() -> {
                            // Find the file
                            VirtualFile file = VirtualFileManager.getInstance().findFileByNioPath(resolvedPath);
                            if (file == null) {
                                return errorResult("Error: File not found: " + resolvedPath);
                            }

                            if (file.isDirectory()) {
                                return errorResult("Error: Path is a directory, not a file: " + resolvedPath);
                            }

                            // Get Document for the file
                            Document document = FileDocumentManager.getInstance().getDocument(file);
                            if (document == null) {
                                return errorResult("Error: Cannot read file (binary or unsupported format): " + resolvedPath);
                            }

                            int totalLines = document.getLineCount();
                            int startLine = startLineOpt.orElse(1);
                            int endLine = endLineOpt.orElse(totalLines);

                            // Validate line range
                            if (startLine < 1) {
                                return errorResult("Error: startLine must be >= 1, got: " + startLine);
                            }
                            if (endLine < startLine) {
                                return errorResult("Error: endLine (" + endLine + ") must be >= startLine (" + startLine + ")");
                            }

                            // Clamp endLine to totalLines
                            if (endLine > totalLines) {
                                endLine = totalLines;
                            }

                            // Extract content for the specified line range
                            int startOffset = document.getLineStartOffset(startLine - 1);
                            int endOffset = document.getLineEndOffset(endLine - 1);
                            String content = document.getText().substring(startOffset, endOffset);

                            return successResult(new ReadFileResponse(
                                    resolvedPath.toString(),
                                    content,
                                    totalLines,
                                    startLine,
                                    endLine
                            ));
                        });
                    } catch (Exception e) {
                        LOG.error("Error in read_file tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record ReadFileResponse(
            String filePath,
            String content,
            int totalLines,
            int startLine,
            int endLine
    ) {}
}
