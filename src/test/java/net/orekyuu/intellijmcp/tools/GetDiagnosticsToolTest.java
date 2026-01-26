package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class GetDiagnosticsToolTest extends BasePlatformTestCase {

    private GetDiagnosticsTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetDiagnosticsTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("get_diagnostics");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("diagnostic");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("projectName")
                .containsKey("errorsOnly");
        // No required parameters
        assertThat(schema.required()).isNullOrEmpty();
    }

    public void testExecuteReturnsResponse() {
        var result = tool.execute(Map.of());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);

        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, GetDiagnosticsTool.GetDiagnosticsResponse>) result;
        GetDiagnosticsTool.GetDiagnosticsResponse response = successResult.message();

        assertThat(response.totalErrors()).isGreaterThanOrEqualTo(0);
        assertThat(response.totalWarnings()).isGreaterThanOrEqualTo(0);
        assertThat(response.files()).isNotNull();
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("get_diagnostics");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
