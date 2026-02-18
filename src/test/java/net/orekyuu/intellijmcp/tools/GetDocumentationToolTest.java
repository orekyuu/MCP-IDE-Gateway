package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GetDocumentationToolTest extends BaseMcpToolTest<GetDocumentationTool> {

    @Override
    GetDocumentationTool createTool() {
        return new GetDocumentationTool();
    }

    @Test
    void executeWithMissingSymbolName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("symbolName");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("symbolName", "SomeSymbol"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
