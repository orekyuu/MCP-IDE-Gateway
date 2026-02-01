package net.orekyuu.intellijmcp.tools;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that runs IntelliJ inspections on a file or project.
 */
public class RunInspectionTool extends AbstractMcpTool<RunInspectionTool.InspectionResponse> {

    private static final Logger LOG = Logger.getInstance(RunInspectionTool.class);
    private static final int DEFAULT_MAX_PROBLEMS = 100;

    @Override
    public String getName() {
        return "run_inspection";
    }

    @Override
    public String getDescription() {
        return "Run IntelliJ code inspections on a file or the entire project to find potential issues, code smells, and improvements";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalString("filePath", "Absolute path to a specific file to inspect (optional, inspects entire project if not specified)")
                .optionalString("inspectionName", "Name of a specific inspection to run (optional, runs all enabled inspections if not specified)")
                .optionalString("minSeverity", "Minimum severity level to report: ERROR, WARNING, WEAK_WARNING, or INFO (default: INFO, reports all)")
                .optionalInteger("maxProblems", "Maximum number of problems to report (default: 100)")
                .build();
    }

    @Override
    public Result<ErrorResponse, InspectionResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                Optional<String> filePath = getStringArg(arguments, "filePath");
                String projectPath;
                try {
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: projectPath is required");
                }
                Optional<String> inspectionName = getStringArg(arguments, "inspectionName");
                String minSeverity = getStringArg(arguments, "minSeverity").orElse("INFO");
                int minSeverityLevel = getSeverityLevel(minSeverity);
                int maxProblems = arguments.containsKey("maxProblems")
                        ? ((Number) arguments.get("maxProblems")).intValue()
                        : DEFAULT_MAX_PROBLEMS;

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Determine scope
                PsiFile targetFile = null;
                if (filePath.isPresent()) {
                    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath.get());
                    if (virtualFile == null) {
                        return errorResult("Error: File not found: " + filePath.get());
                    }
                    targetFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (targetFile == null) {
                        return errorResult("Error: Cannot parse file: " + filePath.get());
                    }
                }

                // Get inspection profile
                InspectionProfileImpl profile = InspectionProjectProfileManager
                        .getInstance(project).getCurrentProfile();

                // Run inspections
                final List<InspectionProblem> problems = new ArrayList<>();

                if (targetFile != null) {
                    // Inspect single file
                    collectProblemsFromFile(profile, targetFile, inspectionName.orElse(null), minSeverityLevel, maxProblems, problems);
                } else {
                    // Inspect project - use ProjectFileIndex to iterate all source files (no recursion)
                    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
                    PsiManager psiManager = PsiManager.getInstance(project);
                    String inspName = inspectionName.orElse(null);

                    fileIndex.iterateContent(file -> {
                        if (problems.size() >= maxProblems) return false;
                        if (file.isDirectory() || !fileIndex.isInSourceContent(file)) return true;

                        PsiFile psiFile = psiManager.findFile(file);
                        if (psiFile != null) {
                            collectProblemsFromFile(profile, psiFile, inspName, minSeverityLevel, maxProblems, problems);
                        }
                        return problems.size() < maxProblems;
                    });
                }

                // Limit results (create new list to avoid modifying original)
                List<InspectionProblem> resultProblems = problems.size() > maxProblems
                        ? problems.subList(0, maxProblems)
                        : problems;

                // Group by severity
                int errors = 0, warnings = 0, weakWarnings = 0, infos = 0;
                for (InspectionProblem p : resultProblems) {
                    switch (p.severity()) {
                        case "ERROR" -> errors++;
                        case "WARNING" -> warnings++;
                        case "WEAK_WARNING" -> weakWarnings++;
                        default -> infos++;
                    }
                }

                return successResult(new InspectionResponse(
                        resultProblems.size(),
                        errors,
                        warnings,
                        weakWarnings,
                        infos,
                        resultProblems
                ));

            } catch (Exception e) {
                LOG.error("Error in run_inspection tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private void collectProblemsFromFile(InspectionProfileImpl profile,
                                          PsiFile psiFile, String inspectionName,
                                          int minSeverityLevel, int maxProblems,
                                          List<InspectionProblem> problems) {
        if (problems.size() >= maxProblems) return;

        // Get enabled LocalInspectionToolWrappers
        List<LocalInspectionToolWrapper> toolWrappers = profile.getInspectionTools(psiFile).stream()
                .filter(t -> t instanceof LocalInspectionToolWrapper)
                .map(t -> (LocalInspectionToolWrapper) t)
                .filter(t -> profile.isToolEnabled(t.getDisplayKey(), psiFile))
                .filter(t -> matchesInspectionName(t, inspectionName))
                .toList();

        if (toolWrappers.isEmpty()) return;

        try {
            // Use InspectionEngine for efficient batch inspection
            Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> results =
                    InspectionEngine.inspectEx(toolWrappers, psiFile, psiFile.getTextRange(),
                            psiFile.getTextRange(), false, false, true,
                            new EmptyProgressIndicator(), (wrapper, descriptor) -> true);

            // Process results
            for (var entry : results.entrySet()) {
                if (problems.size() >= maxProblems) break;

                LocalInspectionToolWrapper toolWrapper = entry.getKey();
                for (ProblemDescriptor descriptor : entry.getValue()) {
                    if (problems.size() >= maxProblems) break;

                    // Filter by severity
                    String severity = getSeverityString(descriptor.getHighlightType());
                    if (getSeverityLevel(severity) < minSeverityLevel) {
                        continue;
                    }

                    InspectionProblem problem = createProblem(psiFile, descriptor, toolWrapper);
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Inspection failed for file " + psiFile.getName() + ": " + e.getMessage());
        }
    }

    private boolean matchesInspectionName(InspectionToolWrapper<?, ?> toolWrapper, String inspectionName) {
        if (inspectionName == null) return true;
        return toolWrapper.getShortName().contains(inspectionName)
                || toolWrapper.getDisplayName().toLowerCase().contains(inspectionName.toLowerCase());
    }

    private InspectionProblem createProblem(PsiFile psiFile, ProblemDescriptor descriptor,
                                            InspectionToolWrapper<?, ?> toolWrapper) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return null;
        }

        String filePath = psiFile.getVirtualFile() != null ? psiFile.getVirtualFile().getPath() : null;
        String message = descriptor.getDescriptionTemplate();
        String severity = getSeverityString(descriptor.getHighlightType());
        String inspectionId = toolWrapper.getShortName();
        String inspectionDisplayName = toolWrapper.getDisplayName();

        LineRange lineRange = getLineRange(element);

        // Get quick fix descriptions
        List<String> quickFixes = new ArrayList<>();
        QuickFix<?>[] fixes = descriptor.getFixes();
        if (fixes != null) {
            for (QuickFix<?> fix : fixes) {
                quickFixes.add(fix.getName());
            }
        }

        return new InspectionProblem(
                filePath,
                message,
                severity,
                inspectionId,
                inspectionDisplayName,
                lineRange,
                quickFixes
        );
    }

    private String getSeverityString(ProblemHighlightType highlightType) {
        return switch (highlightType) {
            case ERROR, GENERIC_ERROR -> "ERROR";
            case WARNING, GENERIC_ERROR_OR_WARNING -> "WARNING";
            case WEAK_WARNING -> "WEAK_WARNING";
            case INFORMATION, LIKE_UNUSED_SYMBOL, LIKE_DEPRECATED, LIKE_MARKED_FOR_REMOVAL, LIKE_UNKNOWN_SYMBOL ->
                    "INFO";
            default -> "INFO";
        };
    }

    private int getSeverityLevel(String severity) {
        return switch (severity.toUpperCase()) {
            case "ERROR" -> 4;
            case "WARNING" -> 3;
            case "WEAK_WARNING" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    private LineRange getLineRange(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        var textRange = element.getTextRange();
        com.intellij.openapi.editor.Document document =
                PsiDocumentManager.getInstance(element.getProject()).getDocument(containingFile);
        if (document != null && textRange != null) {
            int startLine = document.getLineNumber(textRange.getStartOffset()) + 1;
            int endLine = document.getLineNumber(textRange.getEndOffset()) + 1;
            return new LineRange(startLine, endLine);
        }
        return null;
    }

    // Response and data records

    public record InspectionResponse(
            int totalProblems,
            int errors,
            int warnings,
            int weakWarnings,
            int infos,
            List<InspectionProblem> problems
    ) {}

    public record InspectionProblem(
            String filePath,
            String message,
            String severity,
            String inspectionId,
            String inspectionName,
            LineRange lineRange,
            List<String> quickFixes
    ) {}
}
