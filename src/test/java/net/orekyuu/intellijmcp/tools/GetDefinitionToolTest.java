package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GetDefinitionToolTest extends BaseMcpToolTest<GetDefinitionTool> {

    @Override
    GetDefinitionTool createTool() {
        return new GetDefinitionTool();
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

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "className", "com.example.MyClass",
                "projectPath", "/nonexistent/project/path"
        ));
        McpToolResultAssert.assertThat(result).isError();
        var error = (McpTool.Result.ErrorResponse<ErrorResponse, GetDefinitionTool.GetDefinitionResponse>) result;
        assertThat(error.message().message()).containsIgnoringCase("not found");
    }

}
