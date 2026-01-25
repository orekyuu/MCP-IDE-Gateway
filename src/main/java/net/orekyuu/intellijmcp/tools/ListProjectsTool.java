package net.orekyuu.intellijmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that lists all open projects in IntelliJ IDEA.
 * Returns project name, base path, and location hash for each open project.
 */
public class ListProjectsTool extends AbstractMcpTool<ListProjectsTool.ListProjectsResponse> {

    private static final Logger LOG = Logger.getInstance(ListProjectsTool.class);

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
    public Result<ErrorResponse, ListProjectsResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                Project[] projects = getOpenProjects();
                var projectInfoList = Arrays.stream(projects)
                      .map(p -> new ListProjectsResponse.ProjectInfo(p.getName(), p.getBasePath(), p.getLocationHash()))
                      .toList();

                return successResult(new ListProjectsResponse(projectInfoList));
            } catch (Exception e) {
                LOG.error("Error in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    public record ListProjectsResponse(List<ProjectInfo> projects) {
      public record ProjectInfo(String name, String basePath, String locationHash) {}
    }
}
