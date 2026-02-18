package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GetTypeHierarchyToolTest extends BaseMcpToolTest<GetTypeHierarchyTool> {

    @Override
    GetTypeHierarchyTool createTool() {
        return new GetTypeHierarchyTool();
    }

    @Test
    void executeWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("className");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "SomeClass"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
