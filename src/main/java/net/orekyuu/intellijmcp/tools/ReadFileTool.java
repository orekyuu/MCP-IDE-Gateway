package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Paths;
import java.util.Map;

/**
 * MCP tool that reads the content of a file by its absolute path.
 * Supports optional line range specification (1-based).
 */
public class ReadFileTool extends AbstractMcpTool<ReadFileTool.ReadFileResponse> {

    private static final Logger LOG = Logger.getInstance(ReadFileTool.class);

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Read the content of a file by its absolute path. Supports optional line range.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("filePath", "Absolute path to the file to read")
                .optionalInteger("startLine", "Start line number (1-based, inclusive). If not specified, reads from the beginning.")
                .optionalInteger("endLine", "End line number (1-based, inclusive). If not specified, reads to the end.")
                .build();
    }

    @Override
    public Result<ErrorResponse, ReadFileResponse> execute(Map<String, Object> arguments) {
        try {
            String filePath;
            try {
                filePath = getRequiredStringArg(arguments, "filePath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            var startLineOpt = getIntegerArg(arguments, "startLine");
            var endLineOpt = getIntegerArg(arguments, "endLine");

            return runReadActionWithResult(() -> {
                // Find the file
                VirtualFile file = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath));
                if (file == null) {
                    return errorResult("Error: File not found: " + filePath);
                }

                if (file.isDirectory()) {
                    return errorResult("Error: Path is a directory, not a file: " + filePath);
                }

                // Get Document for the file
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document == null) {
                    return errorResult("Error: Cannot read file (binary or unsupported format): " + filePath);
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
                        filePath,
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
    }

    public record ReadFileResponse(
            String filePath,
            String content,
            int totalLines,
            int startLine,
            int endLine
    ) {}
}
