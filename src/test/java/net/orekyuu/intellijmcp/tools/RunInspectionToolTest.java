package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RunInspectionToolTest extends BaseMcpToolTest<RunInspectionTool> {

    @Override
    RunInspectionTool createTool() {
        return new RunInspectionTool();
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
    void shouldSkipEditorConfigInspectionForJavaFile() {
        assertThat(shouldSkipInspection("EditorConfigVerifyByCore", "java")).isTrue();
        assertThat(shouldSkipInspection("EditorConfigCharsetInspection", "java")).isTrue();
        assertThat(shouldSkipInspection("JsonDuplicatePropertyKeys", "java")).isTrue();
        assertThat(shouldSkipInspection("YamlUnresolvedAlias", "java")).isTrue();
    }

    @Test
    void shouldNotSkipEditorConfigInspectionForEditorConfigFile() {
        assertThat(shouldSkipInspection("EditorConfigVerifyByCore", "editorconfig")).isFalse();
    }

    @Test
    void shouldNotSkipJsonInspectionForJsonFile() {
        assertThat(shouldSkipInspection("JsonDuplicatePropertyKeys", "json")).isFalse();
    }

    @Test
    void shouldNotSkipJavaInspectionForJavaFile() {
        assertThat(shouldSkipInspection("UnusedDeclaration", "java")).isFalse();
        assertThat(shouldSkipInspection("JavaDoc", "java")).isFalse();
    }

    /**
     * Simulates the filtering logic in RunInspectionTool.collectProblemsFromFile
     */
    private boolean shouldSkipInspection(String shortName, String fileExtension) {
        if (shortName.startsWith("EditorConfig") || shortName.startsWith("Json") || shortName.startsWith("Yaml")) {
            return fileExtension == null ||
                   (!fileExtension.equals("editorconfig") &&
                    !fileExtension.equals("json") &&
                    !fileExtension.equals("yaml") &&
                    !fileExtension.equals("yml"));
        }
        return false;
    }
}
