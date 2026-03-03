package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
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
    public String getDescription() {
        return "List all open IntelliJ IDEA projects with their paths and Git info. Call this first to get the projectPath required by most other tools.";
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
                              getGitRemote(p),
                              getGitBranch(p)
                      ))
                      .toList();

                return successResult(new ListProjectsResponse(projectInfoList));
            } catch (Exception e) {
                LOG.error("Error in list_projects tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private GitRepository findRepository(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (root == null) return null;
        return GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root);
    }

    private String getGitRemote(Project project) {
        GitRepository repo = findRepository(project);
        if (repo == null) return null;
        return repo.getRemotes().stream()
                .filter(r -> r.getName().equals("origin"))
                .findFirst()
                .map(GitRemote::getFirstUrl)
                .orElse(null);
    }

    private String getGitBranch(Project project) {
        GitRepository repo = findRepository(project);
        if (repo == null) return null;
        GitLocalBranch branch = repo.getCurrentBranch();
        return branch != null ? branch.getName() : null;
    }

    public record ListProjectsResponse(List<ProjectInfo> projects) {
      public record ProjectInfo(String name, String basePath, String gitRemote, String gitBranch) {}
    }
}
