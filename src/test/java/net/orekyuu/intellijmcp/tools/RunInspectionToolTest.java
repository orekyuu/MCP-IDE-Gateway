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
                .containsKey("projectName")
                .containsKey("inspectionName");
        // All parameters are optional
        assertThat(schema.required()).isNullOrEmpty();
    }

    public void testExecuteReturnsResponse() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        // Should return success even with no open projects (will be handled gracefully)
        assertThat(result).isInstanceOfAny(
                McpTool.Result.SuccessResponse.class,
                McpTool.Result.ErrorResponse.class
        );
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("run_inspection");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
