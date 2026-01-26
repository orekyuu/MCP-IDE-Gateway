package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetTypeHierarchyToolTest extends BasePlatformTestCase {

    private GetTypeHierarchyTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetTypeHierarchyTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_type_hierarchy");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("hierarchy");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("className")
                .containsKey("projectName")
                .containsKey("includeSubclasses");
        assertThat(schema.required())
                .isNotNull()
                .contains("className");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetTypeHierarchyTool.TypeHierarchyResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_type_hierarchy");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
