package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class RunInspectionToolTest extends BasePlatformTestCase {

    private RunInspectionTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new RunInspectionTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("run_inspection");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("inspection");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("projectPath")
                .containsKey("inspectionName");
        assertThat(schema.required())
                .isNotNull()
                .contains("projectPath");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, RunInspectionTool.InspectionResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of("projectPath", "/nonexistent/project/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, RunInspectionTool.InspectionResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("run_inspection");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }

    public void testShouldSkipEditorConfigInspectionForJavaFile() {
        // Test the filtering logic: EditorConfig inspections should be skipped for .java files
        assertThat(shouldSkipInspection("EditorConfigVerifyByCore", "java")).isTrue();
        assertThat(shouldSkipInspection("EditorConfigCharsetInspection", "java")).isTrue();
        assertThat(shouldSkipInspection("JsonDuplicatePropertyKeys", "java")).isTrue();
        assertThat(shouldSkipInspection("YamlUnresolvedAlias", "java")).isTrue();
    }

    public void testShouldNotSkipEditorConfigInspectionForEditorConfigFile() {
        // EditorConfig inspections should NOT be skipped for .editorconfig files
        assertThat(shouldSkipInspection("EditorConfigVerifyByCore", "editorconfig")).isFalse();
    }

    public void testShouldNotSkipJsonInspectionForJsonFile() {
        // Json inspections should NOT be skipped for .json files
        assertThat(shouldSkipInspection("JsonDuplicatePropertyKeys", "json")).isFalse();
    }

    public void testShouldNotSkipJavaInspectionForJavaFile() {
        // Java inspections should NOT be skipped for .java files
        assertThat(shouldSkipInspection("UnusedDeclaration", "java")).isFalse();
        assertThat(shouldSkipInspection("JavaDoc", "java")).isFalse();
    }

    /**
     * Simulates the filtering logic in RunInspectionTool.collectProblemsFromFile
     */
    private boolean shouldSkipInspection(String shortName, String fileExtension) {
        if (shortName.startsWith("EditorConfig") || shortName.startsWith("Json") || shortName.startsWith("Yaml")) {
            if (fileExtension == null ||
                (!fileExtension.equals("editorconfig") &&
                 !fileExtension.equals("json") &&
                 !fileExtension.equals("yaml") &&
                 !fileExtension.equals("yml"))) {
                return true;
            }
        }
        return false;
    }
}
