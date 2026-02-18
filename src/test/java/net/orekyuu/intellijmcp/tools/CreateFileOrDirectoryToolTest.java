package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CreateFileOrDirectoryToolTest extends BaseMcpToolTest<CreateFileOrDirectoryTool> {

    @Override
    CreateFileOrDirectoryTool createTool() {
        return new CreateFileOrDirectoryTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("path", "src/Foo.java", "isDirectory", false));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingPath() {
        var result = tool.execute(Map.of("projectPath", "/some/path", "isDirectory", false));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("path");
    }

    @Test
    void executeWithMissingIsDirectory() {
        var result = tool.execute(Map.of("projectPath", "/some/path", "path", "src/Foo.java"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("isDirectory");
    }

    @Test
    void executeWithPathOutsideProject() {
        var args = new HashMap<String, Object>();
        args.put("projectPath", "/some/path");
        args.put("path", "../../etc/passwd");
        args.put("isDirectory", false);

        var result = tool.execute(args);
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("outside the project directory");
    }

}
