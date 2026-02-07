package net.orekyuu.intellijmcp.tools;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool that runs IntelliJ inspections on a file or project.
 * Supports LocalInspectionTool, GlobalSimpleInspectionTool, and GlobalInspectionTool.
 */
public class RunInspectionTool extends AbstractMcpTool<RunInspectionTool.InspectionResponse> {

    private static final Logger LOG = Logger.getInstance(RunInspectionTool.class);
    private static final int DEFAULT_MAX_PROBLEMS = 100;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

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
                .optionalStringArray("inspectionNames", "Names of specific inspections to run (optional, runs all enabled inspections if not specified)")
                .optionalString("minSeverity", "Minimum severity level to report: ERROR, WARNING, WEAK_WARNING, or INFO (default: INFO, reports all)")
                .optionalInteger("maxProblems", "Maximum number of problems to report (default: 100)")
                .optionalInteger("timeout", "Timeout in seconds (default: 60). Returns partial results if timeout is reached.")
                .build();
    }

    @Override
    public Result<ErrorResponse, InspectionResponse> execute(Map<String, Object> arguments) {
        try {
            // Parse arguments (no read action needed)
            Optional<String> filePath = getStringArg(arguments, "filePath");
            String projectPath;
            try {
                projectPath = getRequiredStringArg(arguments, "projectPath");
            } catch (IllegalArgumentException e) {
                return errorResult("Error: projectPath is required");
            }
            List<String> inspectionNames = getStringListArg(arguments, "inspectionNames");
            String minSeverity = getStringArg(arguments, "minSeverity").orElse("INFO");
            int minSeverityLevel = getSeverityLevel(minSeverity);
            int maxProblems = arguments.containsKey("maxProblems")
                    ? ((Number) arguments.get("maxProblems")).intValue()
                    : DEFAULT_MAX_PROBLEMS;
            int timeoutSeconds = arguments.containsKey("timeout")
                    ? ((Number) arguments.get("timeout")).intValue()
                    : DEFAULT_TIMEOUT_SECONDS;
            long timeoutMillis = timeoutSeconds * 1000L;
            long startTime = System.currentTimeMillis();

            // Find project (small read action)
            Optional<Project> projectOpt = findProjectByPath(projectPath);
            if (projectOpt.isEmpty()) {
                return errorResult("Error: Project not found at path: " + projectPath);
            }
            Project project = projectOpt.get();

            // Determine scope (small read action)
            AtomicReference<PsiFile> targetFileRef = new AtomicReference<>();
            if (filePath.isPresent()) {
                String filePathStr = filePath.get();
                String error = ReadAction.compute(() -> {
                    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePathStr);
                    if (virtualFile == null) {
                        return "Error: File not found: " + filePathStr;
                    }
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile == null) {
                        return "Error: Cannot parse file: " + filePathStr;
                    }
                    targetFileRef.set(psiFile);
                    return null;
                });
                if (error != null) {
                    return errorResult(error);
                }
            }

            // Get inspection profile (small read action)
            InspectionProfileImpl profile = ReadAction.compute(() ->
                    InspectionProjectProfileManager.getInstance(project).getCurrentProfile()
            );

            // Run inspections
            final List<InspectionProblem> problems = Collections.synchronizedList(new ArrayList<>());
            boolean[] timedOut = {false};

            PsiFile targetFile = targetFileRef.get();
            if (targetFile != null) {
                // Inspect single file
                collectProblemsFromFile(project, profile, targetFile, inspectionNames, minSeverityLevel, maxProblems, problems, startTime, timeoutMillis, timedOut);
            } else {
                // Inspect project - collect files first, then process
                List<VirtualFile> filesToInspect = ReadAction.compute(() -> {
                    List<VirtualFile> files = new ArrayList<>();
                    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
                    fileIndex.iterateContent(file -> {
                        if (!file.isDirectory() && fileIndex.isInSourceContent(file)) {
                            files.add(file);
                        }
                        return true;
                    });
                    return files;
                });

                // Process each file with separate read actions
                PsiManager psiManager = PsiManager.getInstance(project);
                for (VirtualFile file : filesToInspect) {
                    if (problems.size() >= maxProblems) break;

                    // Check timeout
                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        timedOut[0] = true;
                        LOG.info("Inspection timed out after " + timeoutSeconds + " seconds");
                        break;
                    }

                    // Allow cancellation between files
                    ProgressManager.checkCanceled();

                    // Get PsiFile in a small read action
                    PsiFile psiFile = ReadAction.compute(() -> psiManager.findFile(file));
                    if (psiFile != null) {
                        collectProblemsFromFile(project, profile, psiFile, inspectionNames, minSeverityLevel, maxProblems, problems, startTime, timeoutMillis, timedOut);
                    }
                }
            }

            // Limit results
            List<InspectionProblem> resultProblems = problems.size() > maxProblems
                    ? new ArrayList<>(problems.subList(0, maxProblems))
                    : new ArrayList<>(problems);

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
                    timedOut[0],
                    resultProblems
            ));

        } catch (Exception e) {
            LOG.error("Error in run_inspection tool", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    private void collectProblemsFromFile(Project project, InspectionProfileImpl profile,
                                         PsiFile psiFile, List<String> inspectionNames,
                                         int minSeverityLevel, int maxProblems,
                                         List<InspectionProblem> problems,
                                         long startTime, long timeoutMillis, boolean[] timedOut) {
        if (problems.size() >= maxProblems) return;
        if (timedOut[0]) return;

        // Collect tool wrappers in a read action
        List<LocalInspectionToolWrapper> localTools = new ArrayList<>();
        List<GlobalInspectionToolWrapper> globalSimpleTools = new ArrayList<>();

        ReadAction.run(() -> {
            for (InspectionToolWrapper<?, ?> wrapper : profile.getInspectionTools(psiFile)) {
                if (!profile.isToolEnabled(wrapper.getDisplayKey(), psiFile)) continue;
                if (!matchesInspectionNames(wrapper, inspectionNames)) continue;

                if (wrapper instanceof LocalInspectionToolWrapper localWrapper) {
                    localTools.add(localWrapper);
                } else if (wrapper instanceof GlobalInspectionToolWrapper globalWrapper) {
                    GlobalInspectionTool tool = globalWrapper.getTool();
                    if (tool.isGlobalSimpleInspectionTool()) {
                        globalSimpleTools.add(globalWrapper);
                    }
                }
            }
        });

        // Check timeout before running inspections
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            timedOut[0] = true;
            return;
        }

        // Run local inspections (uses read action internally)
        if (!localTools.isEmpty() && !timedOut[0]) {
            runLocalInspections(localTools, psiFile, minSeverityLevel, maxProblems, problems, startTime, timeoutMillis, timedOut);
        }

        // Run global simple inspections
        if (!globalSimpleTools.isEmpty() && !timedOut[0]) {
            runGlobalSimpleInspections(project, globalSimpleTools, psiFile, minSeverityLevel, maxProblems, problems, startTime, timeoutMillis, timedOut);
        }
    }

    private void runLocalInspections(List<LocalInspectionToolWrapper> toolWrappers,
                                     PsiFile psiFile,
                                     int minSeverityLevel, int maxProblems,
                                     List<InspectionProblem> problems,
                                     long startTime, long timeoutMillis, boolean[] timedOut) {
        try {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                timedOut[0] = true;
                return;
            }

            // InspectionEngine.inspectEx handles read action internally
            Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> results = ReadAction.compute(() ->
                    InspectionEngine.inspectEx(toolWrappers, psiFile, psiFile.getTextRange(),
                            psiFile.getTextRange(), false, false, true,
                            new EmptyProgressIndicator(), (wrapper, descriptor) -> true)
            );

            // Process results outside of read action where possible
            for (var entry : results.entrySet()) {
                if (problems.size() >= maxProblems || timedOut[0]) break;

                // Check timeout periodically
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    timedOut[0] = true;
                    break;
                }

                LocalInspectionToolWrapper toolWrapper = entry.getKey();
                for (ProblemDescriptor descriptor : entry.getValue()) {
                    if (problems.size() >= maxProblems) break;

                    String severity = getSeverityString(descriptor.getHighlightType());
                    if (getSeverityLevel(severity) < minSeverityLevel) continue;

                    // Create problem in read action (needs PSI access)
                    InspectionProblem problem = ReadAction.compute(() -> createProblem(psiFile, descriptor, toolWrapper));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Local inspection failed for file " + psiFile.getName() + ": " + e.getMessage());
        }
    }

    private void runGlobalSimpleInspections(Project project,
                                            List<GlobalInspectionToolWrapper> toolWrappers,
                                            PsiFile psiFile,
                                            int minSeverityLevel, int maxProblems,
                                            List<InspectionProblem> problems,
                                            long startTime, long timeoutMillis, boolean[] timedOut) {
        InspectionManager inspectionManager = InspectionManager.getInstance(project);

        for (GlobalInspectionToolWrapper toolWrapper : toolWrappers) {
            if (problems.size() >= maxProblems || timedOut[0]) break;

            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                timedOut[0] = true;
                break;
            }

            try {
                GlobalInspectionTool tool = toolWrapper.getTool();

                // Run checkFile in read action
                List<ProblemDescriptor> descriptors = ReadAction.compute(() -> {
                    ProblemsHolder holder = new ProblemsHolder(inspectionManager, psiFile, false);
                    ProblemDescriptionsProcessor processor = new SimpleProblemDescriptionsProcessor();
                    tool.checkFile(psiFile, inspectionManager, holder, createSimpleGlobalContext(project), processor);
                    return holder.getResults();
                });

                // Process results
                for (ProblemDescriptor descriptor : descriptors) {
                    if (problems.size() >= maxProblems) break;

                    String severity = getSeverityString(descriptor.getHighlightType());
                    if (getSeverityLevel(severity) < minSeverityLevel) continue;

                    InspectionProblem problem = ReadAction.compute(() -> createProblem(psiFile, descriptor, toolWrapper));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
            } catch (Exception e) {
                LOG.debug("Global simple inspection failed for " + toolWrapper.getDisplayName() + ": " + e.getMessage());
            }
        }
    }

    private GlobalInspectionContextBase createSimpleGlobalContext(Project project) {
        return new GlobalInspectionContextBase(project) {
            @Override
            protected void runTools(AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
                // No-op - we run tools manually
            }
        };
    }

    private boolean matchesInspectionNames(InspectionToolWrapper<?, ?> toolWrapper, List<String> inspectionNames) {
        if (inspectionNames == null || inspectionNames.isEmpty()) return true;
        String shortName = toolWrapper.getShortName().toLowerCase();
        String displayName = toolWrapper.getDisplayName().toLowerCase();
        return inspectionNames.stream().anyMatch(name -> {
            String lowerName = name.toLowerCase();
            return shortName.contains(lowerName) || displayName.contains(lowerName);
        });
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

    // Simple processor that does nothing (for GlobalSimpleInspectionTool)
    private static class SimpleProblemDescriptionsProcessor implements ProblemDescriptionsProcessor {
        // All methods have default implementations in the interface
    }

    // Response and data records

    public record InspectionResponse(
            int totalProblems,
            int errors,
            int warnings,
            int weakWarnings,
            int infos,
            boolean timedOut,
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