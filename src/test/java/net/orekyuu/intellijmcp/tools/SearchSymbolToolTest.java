package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SearchSymbolToolTest extends BaseMcpToolTest<SearchSymbolTool> {

    @Override
    SearchSymbolTool createTool() {
        return new SearchSymbolTool();
    }

    @Test
    void executeWithMissingQuery() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("query");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("query", "test"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of("query", "test", "projectPath", "/nonexistent/project/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

}
