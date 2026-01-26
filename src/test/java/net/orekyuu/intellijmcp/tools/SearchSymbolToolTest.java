package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class SearchSymbolToolTest extends BasePlatformTestCase {

    private SearchSymbolTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new SearchSymbolTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("search_symbol");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("symbol");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("query")
                .containsKey("projectName")
                .containsKey("symbolType");
        assertThat(schema.required())
                .isNotNull()
                .contains("query");
    }

    public void testExecuteWithMissingQuery() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, SearchSymbolTool.SearchSymbolResponse>) result;
        assertThat(errorResult.message().message()).contains("query");
    }

    public void testExecuteWithQuery() {
        var result = tool.execute(Map.of("query", "test"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);

        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchSymbolTool.SearchSymbolResponse>) result;
        SearchSymbolTool.SearchSymbolResponse response = successResult.message();

        assertThat(response.query()).isEqualTo("test");
        assertThat(response.symbols()).isNotNull();
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("search_symbol");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
