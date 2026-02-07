package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class McpToolRegistryTest {

    @Test
    void createDefaultRegistersExpectedTools() {
        McpToolRegistry registry = McpToolRegistry.createDefault();

        List<McpTool<?>> tools = registry.getTools();
        assertThat(tools).isNotNull();
        assertThat(tools).hasSizeGreaterThanOrEqualTo(2);

        List<String> toolNames = tools.stream().map(McpTool::getName).toList();
        assertThat(toolNames)
                .contains("list_projects", "open_file", "get_call_hierarchy");
    }

    @Test
    void registerAddsTool() {
        McpToolRegistry registry = new McpToolRegistry();
        assertThat(registry.size()).isZero();

        registry.register(new ListProjectsTool());
        assertThat(registry.size()).isEqualTo(1);

        registry.register(new OpenFileTool());
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void getToolsReturnsUnmodifiableList() {
        McpToolRegistry registry = McpToolRegistry.createDefault();
        List<McpTool<?>> tools = registry.getTools();

        assertThatThrownBy(() -> tools.add(new ListProjectsTool()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sizeReturnsCorrectCount() {
        McpToolRegistry registry = new McpToolRegistry();
        assertThat(registry.size()).isZero();

        registry.register(new ListProjectsTool());
        assertThat(registry.size()).isEqualTo(1);

        registry.register(new OpenFileTool());
        assertThat(registry.size()).isEqualTo(2);

        registry.register(new CallHierarchyTool());
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void registerCustomTool() {
        McpToolRegistry registry = new McpToolRegistry();

        McpTool<String> customTool = new AbstractMcpTool<>() {
            @Override
            public String getName() {
                return "custom_tool";
            }

            @Override
            public String getDescription() {
                return "A custom test tool";
            }

            @Override
            public io.modelcontextprotocol.spec.McpSchema.JsonSchema getInputSchema() {
                return JsonSchemaBuilder.object()
                        .requiredString("input", "Input parameter")
                        .build();
            }

            @Override
            public McpTool.Result<ErrorResponse, String> execute(java.util.Map<String, Object> arguments) {
                return successResult("Custom tool executed");
            }
        };

        registry.register(customTool);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getTools().getFirst().getName()).isEqualTo("custom_tool");
    }
}
