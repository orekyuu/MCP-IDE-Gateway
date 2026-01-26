package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetDocumentationToolTest extends BasePlatformTestCase {

    private GetDocumentationTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetDocumentationTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_documentation");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("documentation");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("symbolName")
                .containsKey("projectName");
        assertThat(schema.required())
                .isNotNull()
                .contains("symbolName");
    }

    public void testExecuteWithMissingSymbolName() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, GetDocumentationTool.DocumentationResponse>) result;
        assertThat(errorResult.message().message()).contains("symbolName");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_documentation");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
