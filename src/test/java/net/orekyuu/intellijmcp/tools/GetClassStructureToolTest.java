package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetClassStructureToolTest extends BasePlatformTestCase {

    private GetClassStructureTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetClassStructureTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_class_structure");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("structure");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("className")
                .containsKey("projectName")
                .containsKey("includeInherited");
        assertThat(schema.required())
                .isNotNull()
                .contains("className");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetClassStructureTool.ClassStructureResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testExecuteWithNonExistentClass() {
        var result = tool.execute(Map.of(
                "className", "NonExistentClassName12345"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetClassStructureTool.ClassStructureResponse>) result;
        assertThat(errorResult.message().message()).contains("Class not found");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_class_structure");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}