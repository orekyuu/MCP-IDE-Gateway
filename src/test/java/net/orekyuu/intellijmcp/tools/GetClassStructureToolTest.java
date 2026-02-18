package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

class GetClassStructureToolTest extends BaseMcpToolTest<GetClassStructureTool> {

    @Override
    GetClassStructureTool createTool() {
        return new GetClassStructureTool();
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
