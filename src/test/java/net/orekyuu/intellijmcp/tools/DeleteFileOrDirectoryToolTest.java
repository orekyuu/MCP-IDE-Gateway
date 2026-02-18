package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DeleteFileOrDirectoryToolTest extends BaseMcpToolTest<DeleteFileOrDirectoryTool> {

    @Override
    DeleteFileOrDirectoryTool createTool() {
        return new DeleteFileOrDirectoryTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("path", "src/Foo.java"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingPath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("path");
    }

    @Test
    void executeWithPathOutsideProject() {
        var args = new HashMap<String, Object>();
        args.put("projectPath", "/some/path");
        args.put("path", "../../etc/passwd");

        var result = tool.execute(args);
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("outside the project directory");
    }

}
