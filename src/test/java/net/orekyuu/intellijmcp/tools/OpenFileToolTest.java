package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OpenFileToolTest extends BaseMcpToolTest<OpenFileTool> {

    @Override
    OpenFileTool createTool() {
        return new OpenFileTool();
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of());
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithNonExistentFile() {
        var result = tool.execute(Map.of(
                "filePath", "/nonexistent/path/to/file.java",
                "projectPath", "/some/project"
        ));
        McpToolResultAssert.assertThat(result).isError();
        var error = (McpTool.Result.ErrorResponse<ErrorResponse, OpenFileTool.OpenFileResponse>) result;
        assertThat(error.message().message()).containsIgnoringCase("not found");
    }

    @Test
    void executeWithInvalidProjectPath() {
        var result = tool.execute(Map.of(
                "filePath", "/some/test/file.java",
                "projectPath", "/nonexistent/project/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

}
