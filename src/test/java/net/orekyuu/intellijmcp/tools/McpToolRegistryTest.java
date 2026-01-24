package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    @Test
    void createDefaultRegistersExpectedTools() {
        McpToolRegistry registry = McpToolRegistry.createDefault();

        List<McpTool> tools = registry.getTools();
        assertNotNull(tools);
        assertTrue(tools.size() >= 2);

        // Check that expected tools are registered
        List<String> toolNames = tools.stream().map(McpTool::getName).toList();
        assertTrue(toolNames.contains("list_projects"));
        assertTrue(toolNames.contains("open_file"));
        assertTrue(toolNames.contains("get_call_hierarchy"));
    }

    @Test
    void registerAddsTool() {
        McpToolRegistry registry = new McpToolRegistry();
        assertEquals(0, registry.size());

        registry.register(new ListProjectsTool());
        assertEquals(1, registry.size());

        registry.register(new OpenFileTool());
        assertEquals(2, registry.size());
    }

    @Test
    void getToolsReturnsUnmodifiableList() {
        McpToolRegistry registry = McpToolRegistry.createDefault();
        List<McpTool> tools = registry.getTools();

        assertThrows(UnsupportedOperationException.class, () -> {
            tools.add(new ListProjectsTool());
        });
    }

    @Test
    void sizeReturnsCorrectCount() {
        McpToolRegistry registry = new McpToolRegistry();
        assertEquals(0, registry.size());

        registry.register(new ListProjectsTool());
        assertEquals(1, registry.size());

        registry.register(new OpenFileTool());
        assertEquals(2, registry.size());

        registry.register(new CallHierarchyTool());
        assertEquals(3, registry.size());
    }

    @Test
    void registerCustomTool() {
        McpToolRegistry registry = new McpToolRegistry();

        // Create a custom tool for testing
        McpTool customTool = new AbstractMcpTool() {
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
            public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(java.util.Map<String, Object> arguments) {
                return successResult("Custom tool executed");
            }
        };

        registry.register(customTool);

        assertEquals(1, registry.size());
        assertEquals("custom_tool", registry.getTools().get(0).getName());
    }
}
