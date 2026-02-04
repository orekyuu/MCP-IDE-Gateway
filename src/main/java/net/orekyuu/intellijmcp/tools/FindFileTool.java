package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MCP tool that searches for files by name in a project.
 * Supports exact matching and glob patterns (*, ?).
 */
public class FindFileTool extends AbstractMcpTool<FindFileTool.FindFileResponse> {

    private static final Logger LOG = Logger.getInstance(FindFileTool.class);
    private static final int DEFAULT_MAX_RESULTS = 100;

    @Override
    public String getName() {
        return "find_file";
    }

    @Override
    public String getDescription() {
        return "Search for files by name in a project. Supports glob patterns (* and ?) for flexible matching.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .requiredString("fileName", "File name or pattern to search for. Supports glob patterns (* for any characters, ? for single character). Example: '*.java', 'Test*.kt', 'config.?ml'")
                .optionalBoolean("includeLibraries", "Include library files in search (default: false)")
                .optionalInteger("maxResults", "Maximum number of results to return (default: 100)")
                .build();
    }

    @Override
    public Result<ErrorResponse, FindFileResponse> execute(Map<String, Object> arguments) {
        try {
            // Parse arguments
            String projectPath;
            String fileName;
            try {
                projectPath = getRequiredStringArg(arguments, "projectPath");
                fileName = getRequiredStringArg(arguments, "fileName");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            boolean includeLibraries = getBooleanArg(arguments, "includeLibraries").orElse(false);
            int maxResults = getIntegerArg(arguments, "maxResults").orElse(DEFAULT_MAX_RESULTS);

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Determine search scope
            GlobalSearchScope scope = includeLibraries
                    ? GlobalSearchScope.allScope(project)
                    : GlobalSearchScope.projectScope(project);

            // Perform search
            List<FileInfo> results = performSearch(project, fileName, scope, maxResults);

            return successResult(new FindFileResponse(
                    fileName,
                    includeLibraries,
                    results.size(),
                    results.size() >= maxResults,
                    results
            ));

        } catch (Exception e) {
            LOG.error("Error in find_file tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private List<FileInfo> performSearch(Project project, String fileNamePattern, GlobalSearchScope scope, int maxResults) {
        List<FileInfo> results = new ArrayList<>();

        // Check if pattern contains wildcards
        boolean hasWildcard = fileNamePattern.contains("*") || fileNamePattern.contains("?");

        if (hasWildcard) {
            // Use pattern matching with ProjectFileIndex
            Pattern pattern = createPatternFromGlob(fileNamePattern);
            searchWithPattern(project, pattern, scope, results, maxResults);
        } else {
            // Use exact filename search with FilenameIndex
            searchExact(project, fileNamePattern, scope, results, maxResults);
        }

        return results;
    }

    private void searchExact(Project project, String fileName, GlobalSearchScope scope, List<FileInfo> results, int maxResults) {
        runReadAction(() -> {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(fileName, scope);

            for (VirtualFile file : files) {
                if (results.size() >= maxResults) {
                    break;
                }

                results.add(createFileInfo(project, file));
            }
            return null;
        });
    }

    private void searchWithPattern(Project project, Pattern pattern, GlobalSearchScope scope, List<FileInfo> results, int maxResults) {
        runReadAction(() -> {
            ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

            fileIndex.iterateContent(file -> {
                if (results.size() >= maxResults) {
                    return false; // Stop iteration
                }

                if (!file.isDirectory() && scope.contains(file)) {
                    String name = file.getName();
                    if (pattern.matcher(name).matches()) {
                        results.add(createFileInfo(project, file));
                    }
                }
                return true; // Continue iteration
            });

            return null;
        });
    }

    private FileInfo createFileInfo(Project project, VirtualFile file) {
        String path = file.getPath();
        String name = file.getName();
        String extension = file.getExtension();
        long size = file.getLength();
        String fileType = file.getFileType().getName();

        // Get relative path from project root
        String basePath = project.getBasePath();
        String relativePath = path;
        if (basePath != null && path.startsWith(basePath)) {
            relativePath = path.substring(basePath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
        }

        return new FileInfo(path, relativePath, name, extension, size, fileType);
    }

    /**
     * Converts a glob pattern to a regex pattern.
     * Supports * (any characters) and ? (single character).
     */
    private Pattern createPatternFromGlob(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                case '[', ']', '(', ')', '{', '}', '^', '$', '+', '|' -> regex.append("\\").append(c);
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public record FindFileResponse(
            String pattern,
            boolean includeLibraries,
            int totalResults,
            boolean truncated,
            List<FileInfo> files
    ) {}

    public record FileInfo(
            String path,
            String relativePath,
            String name,
            String extension,
            long size,
            String fileType
    ) {}
}
