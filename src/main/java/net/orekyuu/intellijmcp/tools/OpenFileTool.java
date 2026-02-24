package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.Map;

/**
 * MCP tool that opens a file in the IntelliJ IDEA editor.
 * Supports specifying a target project or using the first available project.
 */
public class OpenFileTool extends AbstractMcpTool<OpenFileTool.OpenFileResponse> {

    private static final Logger LOG = Logger.getInstance(OpenFileTool.class);

    private static final Arg<Path> FILE_PATH =
            Arg.absolutePath("filePath", "Absolute path to the file to open");
    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getDescription() {
        return "Open a file in IntelliJ IDEA editor";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH, PROJECT);
    }

    @Override
    public Result<ErrorResponse, OpenFileResponse> execute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH, PROJECT)
                .mapN((filePath, project) -> {
                    try {

                        // Find the file
                        VirtualFile file = runReadAction(() ->
                                VirtualFileManager.getInstance().findFileByNioPath(filePath)
                        );

                        if (file == null) {
                            return errorResult("Error: File not found: " + filePath);
                        }

                        // Open file on EDT
                        runOnEdt(() -> {
                            LOG.info("Open file: " + filePath + " in project: " + project.getName());
                            FileEditorManager.getInstance(project).openFile(file, true);
                        });

                        return successResult(new OpenFileResponse("File opened successfully: " + filePath));
                    } catch (Exception e) {
                        LOG.error("Error in open_file tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    public record OpenFileResponse(String message) {}
}
