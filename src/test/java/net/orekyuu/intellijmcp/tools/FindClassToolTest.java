package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

class FindClassToolTest extends BaseMcpToolTest<FindClassTool> {

    @Override
    FindClassTool createTool() {
        return new FindClassTool();
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

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "className", "NonExistentClassName12345",
                "projectPath", "/nonexistent/project/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

}
