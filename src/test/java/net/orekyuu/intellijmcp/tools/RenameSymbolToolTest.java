package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RenameSymbolToolTest extends BaseMcpToolTest<RenameSymbolTool> {

    @Override
    RenameSymbolTool createTool() {
        return new RenameSymbolTool();
    }

    @Test
    void executeWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path", "newName", "newSymbolName"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("className");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of(
                "className", "com.example.MyClass",
                "newName", "newSymbolName"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
