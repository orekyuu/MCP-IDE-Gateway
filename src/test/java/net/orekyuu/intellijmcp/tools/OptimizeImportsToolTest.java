package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class OptimizeImportsToolTest extends BasePlatformTestCase {

    private OptimizeImportsTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new OptimizeImportsTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("optimize_imports");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("import");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, OptimizeImportsTool.OptimizeImportsResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("filePath", "/some/file.java"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, OptimizeImportsTool.OptimizeImportsResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("optimize_imports");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
