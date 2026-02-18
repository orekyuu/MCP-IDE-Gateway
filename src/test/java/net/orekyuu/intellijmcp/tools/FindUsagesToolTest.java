package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FindUsagesToolTest extends BaseMcpToolTest<FindUsagesTool> {

    @Override
    FindUsagesTool createTool() {
        return new FindUsagesTool();
    }

    @Test
    void executeWithMissingClassName() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("className");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("className", "com.example.MyClass"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

}
