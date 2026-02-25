package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

class ListRunConfigurationsToolTest extends BaseMcpToolTest<ListRunConfigurationsTool> {

    @Override
    ListRunConfigurationsTool createTool() {
        return new ListRunConfigurationsTool();
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

    @Test
    void executeWithValidProject() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of("projectPath", projectPath));
        McpToolResultAssert.assertThat(result).isSuccess();
    }

    @Test
    void executeWithIncludeTemporaryFalse() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "includeTemporary", false
        ));
        McpToolResultAssert.assertThat(result).isSuccess();
    }

    @Test
    void executeWithIncludeTemporaryTrue() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "includeTemporary", true
        ));
        McpToolResultAssert.assertThat(result).isSuccess();
    }
}
