package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ExtractMethodToolTest extends BaseMcpToolTest<ExtractMethodTool> {

    @Override
    ExtractMethodTool createTool() {
        return new ExtractMethodTool();
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "startLine", 1,
                "endLine", 5,
                "methodName", "newMethod"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
