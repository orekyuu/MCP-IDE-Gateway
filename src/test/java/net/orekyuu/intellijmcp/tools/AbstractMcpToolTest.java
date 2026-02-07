package net.orekyuu.intellijmcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AbstractMcpToolTest {

    // Concrete implementation for testing
    private static class TestTool extends AbstractMcpTool<String> {
        @Override
        public String getName() {
            return "test_tool";
        }

        @Override
        public String getDescription() {
            return "Test tool";
        }

        @Override
        public McpSchema.JsonSchema getInputSchema() {
            return JsonSchemaBuilder.object().build();
        }

        @Override
        public Result<ErrorResponse, String> execute(Map<String, Object> arguments) {
            return successResult("ok");
        }

        // Expose protected methods for testing
        public List<String> testGetStringListArg(Map<String, Object> arguments, String key) {
            return getStringListArg(arguments, key);
        }
    }

    private final TestTool tool = new TestTool();

    @Test
    void getStringListArgWithValidList() {
        Map<String, Object> args = Map.of("names", List.of("foo", "bar", "baz"));

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).containsExactly("foo", "bar", "baz");
    }

    @Test
    void getStringListArgWithEmptyList() {
        Map<String, Object> args = Map.of("names", List.of());

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).isEmpty();
    }

    @Test
    void getStringListArgWithMissingKey() {
        Map<String, Object> args = Map.of();

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).isEmpty();
    }

    @Test
    void getStringListArgWithNullValue() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("names", null);

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).isEmpty();
    }

    @Test
    void getStringListArgWithNonListValue() {
        Map<String, Object> args = Map.of("names", "not a list");

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).isEmpty();
    }

    @Test
    void getStringListArgFiltersNonStringElements() {
        Map<String, Object> args = Map.of("names", List.of("foo", 123, "bar", true));

        List<String> result = tool.testGetStringListArg(args, "names");

        assertThat(result).containsExactly("foo", "bar");
    }
}
