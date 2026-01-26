package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class FindUsagesToolTest extends BasePlatformTestCase {

    private FindUsagesTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new FindUsagesTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("find_usages");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("usage");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("offset")
                .containsKey("projectName");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "offset");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("offset", 0));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, FindUsagesTool.FindUsagesResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithMissingOffset() {
        var result = tool.execute(Map.of("filePath", "/some/path.java"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, FindUsagesTool.FindUsagesResponse>) result;
        assertThat(errorResult.message().message()).contains("offset");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("find_usages");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
