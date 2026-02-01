package net.orekyuu.intellijmcp.tools;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * MCP tool that searches for text in project files.
 * Supports regular expressions and case-sensitive matching.
 */
public class SearchTextTool extends AbstractMcpTool<SearchTextTool.SearchTextResponse> {

    private static final Logger LOG = Logger.getInstance(SearchTextTool.class);
    private static final int MAX_RESULTS = 100;

    @Override
    public String getName() {
        return "search_text";
    }

    @Override
    public String getDescription() {
        return "Search for text in project files. Supports regular expressions and case-sensitive matching.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("searchText", "The text or pattern to search for")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalBoolean("useRegex", "Use regular expression matching (default: false)")
                .optionalBoolean("caseSensitive", "Case-sensitive matching (default: false)")
                .optionalString("filePattern", "File name pattern to filter (e.g., '*.java', '*.xml')")
                .optionalInteger("maxResults", "Maximum number of results to return (default: 100)")
                .build();
    }

    @Override
    public Result<ErrorResponse, SearchTextResponse> execute(Map<String, Object> arguments) {
        try {
            // Get arguments
            String searchText;
            String projectPath;
            try {
                searchText = getRequiredStringArg(arguments, "searchText");
                projectPath = getRequiredStringArg(arguments, "projectPath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: " + e.getMessage());
            }

            boolean useRegex = getBooleanArg(arguments, "useRegex").orElse(false);
            boolean caseSensitive = getBooleanArg(arguments, "caseSensitive").orElse(false);
            String filePattern = getStringArg(arguments, "filePattern").orElse(null);
            int maxResults = getIntegerArg(arguments, "maxResults").orElse(MAX_RESULTS);

            // Validate regex if enabled
            if (useRegex) {
                try {
                    Pattern.compile(searchText);
                } catch (PatternSyntaxException e) {
                    return errorResult("Error: Invalid regular expression: " + e.getMessage());
                }
            }

            // Find project
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Perform search
            List<SearchMatch> matches = performSearch(
                    project, searchText, useRegex, caseSensitive, filePattern, maxResults);

            return successResult(new SearchTextResponse(
                    searchText,
                    useRegex,
                    caseSensitive,
                    filePattern,
                    matches.size(),
                    matches.size() >= maxResults,
                    matches
            ));

        } catch (Exception e) {
            LOG.error("Error in search_text tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private List<SearchMatch> performSearch(
            Project project,
            String searchText,
            boolean useRegex,
            boolean caseSensitive,
            String filePattern,
            int maxResults) {

        List<SearchMatch> matches = new ArrayList<>();

        // Setup FindModel
        FindModel findModel = new FindModel();
        findModel.setStringToFind(searchText);
        findModel.setRegularExpressions(useRegex);
        findModel.setCaseSensitive(caseSensitive);
        findModel.setWholeWordsOnly(false);

        // Get project base directory
        VirtualFile baseDir = runReadAction(() -> ProjectUtil.guessProjectDir(project));
        if (baseDir == null) {
            return matches;
        }

        // Create file pattern matcher if specified
        Pattern filePatternRegex = null;
        if (filePattern != null && !filePattern.isEmpty()) {
            String regex = convertGlobToRegex(filePattern);
            filePatternRegex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }

        Pattern finalFilePatternRegex = filePatternRegex;
        FindManager findManager = FindManager.getInstance(project);

        // Visit all files in the project
        VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (matches.size() >= maxResults) {
                    return false; // Stop visiting
                }

                if (file.isDirectory()) {
                    // Skip hidden directories and common non-source directories
                    String name = file.getName();
                    if (name.startsWith(".") ||
                        name.equals("node_modules") ||
                        name.equals("build") ||
                        name.equals("out") ||
                        name.equals("target")) {
                        return false;
                    }
                    return true;
                }

                // Skip binary files
                if (file.getFileType().isBinary()) {
                    return true;
                }

                // Check file pattern
                if (finalFilePatternRegex != null) {
                    if (!finalFilePatternRegex.matcher(file.getName()).matches()) {
                        return true;
                    }
                }

                // Search in file
                searchInFile(project, findManager, findModel, file, matches, maxResults);
                return true;
            }
        });

        return matches;
    }

    private void searchInFile(
            Project project,
            FindManager findManager,
            FindModel findModel,
            VirtualFile file,
            List<SearchMatch> matches,
            int maxResults) {

        runReadAction(() -> {
            if (!file.isValid()) {
                return null;
            }

            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                return null;
            }

            CharSequence text = document.getCharsSequence();
            int offset = 0;

            while (offset < text.length() && matches.size() < maxResults) {
                FindResult result = findManager.findString(text, offset, findModel, file);

                if (!result.isStringFound()) {
                    break;
                }

                int startOffset = result.getStartOffset();
                int endOffset = result.getEndOffset();

                // Prevent infinite loop for zero-length matches
                if (startOffset == offset && endOffset == offset) {
                    offset++;
                    continue;
                }

                // Get line number (1-based)
                int lineNumber = document.getLineNumber(startOffset) + 1;

                // Get column (1-based)
                int lineStartOffset = document.getLineStartOffset(lineNumber - 1);
                int column = startOffset - lineStartOffset + 1;

                // Get matched text
                String matchedText = text.subSequence(startOffset, endOffset).toString();

                // Get line content
                int lineEndOffset = document.getLineEndOffset(lineNumber - 1);
                String lineContent = text.subSequence(lineStartOffset, lineEndOffset).toString().trim();

                matches.add(new SearchMatch(
                        file.getPath(),
                        lineNumber,
                        column,
                        matchedText,
                        lineContent
                ));

                offset = endOffset;
            }

            return null;
        });
    }

    /**
     * Converts a glob pattern to a regex pattern.
     * Supports * and ? wildcards.
     */
    private String convertGlobToRegex(String glob) {
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
        return regex.toString();
    }

    public record SearchTextResponse(
            String searchText,
            boolean useRegex,
            boolean caseSensitive,
            String filePattern,
            int totalMatches,
            boolean truncated,
            List<SearchMatch> matches
    ) {}

    public record SearchMatch(
            String filePath,
            int line,
            int column,
            String matchedText,
            String lineContent
    ) {}
}