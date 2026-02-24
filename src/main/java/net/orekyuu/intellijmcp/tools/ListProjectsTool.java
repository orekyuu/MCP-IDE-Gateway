package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
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
                      .map(p -> new ListProjectsResponse.ProjectInfo(
                              p.getName(),
                              p.getBasePath(),
                              getGitRemote(p.getBasePath()),
                              getGitBranch(p.getBasePath())
                      ))
                      .toList();

                return successResult(new ListProjectsResponse(projectInfoList));
            } catch (Exception e) {
                LOG.error("Error in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private String getGitRemote(String basePath) {
        if (basePath == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "remote", "get-url", "origin");
            pb.directory(new File(basePath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return process.exitValue() == 0 ? line : null;
            }
        } catch (Exception e) {
            LOG.debug("Failed to get git remote for " + basePath, e);
            return null;
        }
    }

    private String getGitBranch(String basePath) {
        if (basePath == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
            pb.directory(new File(basePath));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return process.exitValue() == 0 ? line : null;
            }
        } catch (Exception e) {
            LOG.debug("Failed to get git branch for " + basePath, e);
            return null;
        }
    }

    public record ListProjectsResponse(List<ProjectInfo> projects) {
      public record ProjectInfo(String name, String basePath, String gitRemote, String gitBranch) {}
    }
}
