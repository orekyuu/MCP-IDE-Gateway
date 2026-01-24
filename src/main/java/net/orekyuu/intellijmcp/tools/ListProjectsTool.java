package net.orekyuu.intellijmcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * MCP tool that lists all open projects in IntelliJ IDEA.
 * Returns project name, base path, and location hash for each open project.
 */
public class ListProjectsTool extends AbstractMcpTool {

    private static final Logger LOG = Logger.getInstance(ListProjectsTool.class);
    private static final Gson GSON = new Gson();

    @Override
    public String getName() {
        return "list_projects";
    }

    @Override
    public String getDescription() {
        return "Get list of open projects in IntelliJ IDEA";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object().build();
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                Project[] projects = getOpenProjects();

                JsonArray projectList = new JsonArray();
                for (Project project : projects) {
                    JsonObject projectInfo = new JsonObject();
                    projectInfo.addProperty("name", project.getName());
                    String basePath = project.getBasePath();
                    if (basePath != null) {
                        projectInfo.addProperty("basePath", basePath);
                    }
                    projectInfo.addProperty("locationHash", project.getLocationHash());
                    projectList.add(projectInfo);
                }

                String result = GSON.toJson(projectList);
                return successResult(result);
            } catch (Exception e) {
                LOG.error("Error in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }
}
