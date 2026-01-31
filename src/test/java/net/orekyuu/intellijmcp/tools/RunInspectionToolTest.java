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
}
