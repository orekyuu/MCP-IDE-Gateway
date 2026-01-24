package net.orekyuu.intellijmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

                ArrayNode projectList = MAPPER.createArrayNode();
                for (Project project : projects) {
                    ObjectNode projectInfo = MAPPER.createObjectNode();
                    projectInfo.put("name", project.getName());
                    String basePath = project.getBasePath();
                    if (basePath != null) {
                        projectInfo.put("basePath", basePath);
                    }
                    projectInfo.put("locationHash", project.getLocationHash());
                    projectList.add(projectInfo);
                }

                String result = MAPPER.writeValueAsString(projectList);
                return successResult(result);
            } catch (JsonProcessingException e) {
                LOG.error("Error serializing JSON in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            } catch (Exception e) {
                LOG.error("Error in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }
}
