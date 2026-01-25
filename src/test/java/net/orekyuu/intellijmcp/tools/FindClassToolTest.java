package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class FindClassToolTest extends BasePlatformTestCase {

    private FindClassTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new FindClassTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("find_class");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("class")
                .containsIgnoringCase("find");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("className")
                .containsKey("projectName")
                .containsKey("includeLibraries");
        assertThat(schema.required())
                .isNotNull()
                .contains("className")
                .doesNotContain("projectName")
                .doesNotContain("includeLibraries");
    }

    public void testExecuteWithMissingClassName() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, FindClassTool.FindClassResponse>) result;
        assertThat(errorResult.message().message()).contains("className");
    }

    public void testExecuteWithNonExistentClass() {
        var result = tool.execute(Map.of(
                "className", "NonExistentClassName12345"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);

        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, FindClassTool.FindClassResponse>) result;
        FindClassTool.FindClassResponse response = successResult.message();

        assertThat(response.searchQuery()).isEqualTo("NonExistentClassName12345");
        assertThat(response.classes()).isEmpty();
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("find_class");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
