package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GetImplementationsToolTest extends BaseMcpToolTest<GetImplementationsTool> {

    @Override
    GetImplementationsTool createTool() {
        return new GetImplementationsTool();
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
