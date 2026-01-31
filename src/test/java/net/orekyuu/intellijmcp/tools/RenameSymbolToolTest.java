package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class RenameSymbolToolTest extends BasePlatformTestCase {

    private RenameSymbolTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new RenameSymbolTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("rename_symbol");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("rename");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("filePath")
                .containsKey("line")
                .containsKey("column")
                .containsKey("newName")
                .containsKey("projectPath");
        assertThat(schema.required())
                .isNotNull()
                .contains("filePath", "line", "column", "newName", "projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, RenameSymbolTool.RenameSymbolResponse>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of(
                "filePath", "/some/file.java",
                "line", 1,
                "column", 1,
                "newName", "newSymbolName"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, RenameSymbolTool.RenameSymbolResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("rename_symbol");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
