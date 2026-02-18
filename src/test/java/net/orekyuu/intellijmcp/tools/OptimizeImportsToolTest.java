package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

class OptimizeImportsToolTest extends BaseMcpToolTest<OptimizeImportsTool> {

    @Override
    OptimizeImportsTool createTool() {
        return new OptimizeImportsTool();
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("filePath", "/some/file.java"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
