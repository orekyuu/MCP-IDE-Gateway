package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

class GetDiagnosticsToolTest extends BaseMcpToolTest<GetDiagnosticsTool> {

    @Override
    GetDiagnosticsTool createTool() {
        return new GetDiagnosticsTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of());
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of("projectPath", "/nonexistent/project/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

}
