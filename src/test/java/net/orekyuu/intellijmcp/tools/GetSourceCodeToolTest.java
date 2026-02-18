package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

class GetSourceCodeToolTest extends BaseMcpToolTest<GetSourceCodeTool> {

    @Override
    GetSourceCodeTool createTool() {
        return new GetSourceCodeTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "com.example.MyClass"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("className");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "className", "com.example.MyClass"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found");
    }

}
