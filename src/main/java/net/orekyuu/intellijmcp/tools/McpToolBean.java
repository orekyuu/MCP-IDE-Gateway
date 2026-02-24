package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.serviceContainer.LazyExtensionInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.services.McpServerLogService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class McpToolBean extends LazyExtensionInstance<McpTool<?>> implements PluginAware {

    public static final ExtensionPointName<McpToolBean> EP_NAME =
            ExtensionPointName.create("net.orekyuu.mcp-ide-gateway.mcpTool");

    @Attribute("implementation")
    public String implementationClass;

    @Attribute("name")
    public String name;

    private PluginDescriptor pluginDescriptor;

    @Override
    public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return implementationClass;
    }

    public McpServerFeatures.SyncToolSpecification toSpecification() {
        McpTool<?> tool = createInstance(ApplicationManager.getApplication(), pluginDescriptor);
        return buildSpecification(tool);
    }

    private <R> McpServerFeatures.SyncToolSpecification buildSpecification(McpTool<R> tool) {
        var toolSpec = McpSchema.Tool.builder()
                .name(name)
                .description(tool.getDescription())
                .inputSchema(tool.getInputSchema())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(toolSpec)
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments();
                    McpServerLogService logService = McpServerLogService.getInstance();
                    logService.info("Tool call: " + name);
                    logService.info("  Request: " + ResponseSerializer.serialize(arguments));
                    LocalFileSystem.getInstance().refresh(false);
                    McpTool.Result<ErrorResponse, R> result = tool.execute(arguments);
                    return switch (result) {
                        case McpTool.Result.ErrorResponse<ErrorResponse, R> err -> {
                            logService.error("  Response (error): " + err.message().message());
                            yield McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(err.message().message())))
                                    .isError(true).build();
                        }
                        case McpTool.Result.SuccessResponse<ErrorResponse, R> success -> {
                            String body = ResponseSerializer.serialize(success.message());
                            logService.info("  Response: " + body);
                            yield McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(body)))
                                    .isError(false).build();
                        }
                    };
                })
                .build();
    }
}
