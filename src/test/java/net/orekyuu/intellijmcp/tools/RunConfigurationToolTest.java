package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

class RunConfigurationToolTest extends BaseMcpToolTest<RunConfigurationTool> {

    @Override
    RunConfigurationTool createTool() {
        return new RunConfigurationTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("name", "MyApp"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingName() {
        var result = tool.execute(Map.of("projectPath", "/some/project"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("name");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "name", "MyApp"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithNonExistentConfiguration() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "name", "NonExistentConfiguration12345"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("not found");
    }
}
