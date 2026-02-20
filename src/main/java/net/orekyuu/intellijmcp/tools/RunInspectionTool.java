package net.orekyuu.intellijmcp.tools;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import org.jetbrains.annotations.NotNull;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import net.orekyuu.intellijmcp.tools.validator.ProjectRelativePath;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool that runs IntelliJ inspections on a file or project.
 * Supports LocalInspectionTool, GlobalSimpleInspectionTool, and GlobalInspectionTool.
 */
public class RunInspectionTool extends AbstractProjectMcpTool<RunInspectionTool.InspectionResponse> {

    private static final Logger LOG = Logger.getInstance(RunInspectionTool.class);

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Optional<ProjectRelativePath>> FILE_PATH =
            Arg.optionalProjectRelativePath("filePath", "Relative path from the project root to a specific file to inspect (optional, inspects entire project if not specified)");
    private static final Arg<List<String>> INSPECTION_NAMES =
            Arg.stringArray("inspectionNames", "Names of specific inspections to run (optional, runs all enabled inspections if not specified)").optional();
    public enum Severity {
        ERROR(4), WARNING(3), WEAK_WARNING(2), INFO(1);

        private final int level;

        Severity(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }

    private static final Arg<Severity> MIN_SEVERITY =
            Arg.enumArg("minSeverity", "Minimum severity level to report", Severity.class).optional(Severity.INFO);
    private static final Arg<Integer> MAX_PROBLEMS =
            Arg.integer("maxProblems", "Maximum number of problems to report").optional(100);
    private static final Arg<Integer> TIMEOUT =
            Arg.integer("timeout", "Timeout in seconds. Returns partial results if timeout is reached.").min(0).optional(60);

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
        return Args.schema(PROJECT, FILE_PATH, INSPECTION_NAMES, MIN_SEVERITY, MAX_PROBLEMS, TIMEOUT);
    }

    @Override
    @SuppressWarnings("resource") // ScheduledExecutorService is properly shutdown in finally block
    public Result<ErrorResponse, InspectionResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, FILE_PATH, INSPECTION_NAMES, MIN_SEVERITY, MAX_PROBLEMS, TIMEOUT)
                .mapN((project, filePathOpt, inspectionNames, minSeverity, maxProblems, timeoutSeconds) -> {
                    try {
                        return executeInspection(project, filePathOpt, inspectionNames, minSeverity, maxProblems, timeoutSeconds);
                    } catch (Exception e) {
                        LOG.error("Error in run_inspection tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private Result<ErrorResponse, InspectionResponse> executeInspection(
            Project project, Optional<ProjectRelativePath> filePathOpt,
            List<String> inspectionNames, Severity minSeverity,
            int maxProblems, int timeoutSeconds) {
        try {
            String projectPath = project.getBasePath();
            int minSeverityLevel = minSeverity.level();
            long timeoutMillis = timeoutSeconds * 1000L;
            long startTime = System.currentTimeMillis();

            // Determine scope (small read action)
            AtomicReference<PsiFile> targetFileRef = new AtomicReference<>();
            if (filePathOpt.isPresent()) {
                Path resolvedPath = filePathOpt.get().resolve(project);
                String absolutePath = resolvedPath.toString();
                String error = ReadAction.compute(() -> {
                    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath);
                    if (virtualFile == null) {
                        return "Error: File not found: " + absolutePath;
                    }
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile == null) {
                        return "Error: Cannot parse file: " + absolutePath;
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

            // Create a cancellable progress indicator for timeout support
            ProgressIndicatorBase indicator = new ProgressIndicatorBase();
            long remainingMillis = timeoutMillis - (System.currentTimeMillis() - startTime);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
                timedOut[0] = true;
                indicator.cancel();
                LOG.info("Inspection timed out after " + timeoutSeconds + " seconds");
            }, Math.max(remainingMillis, 0), TimeUnit.MILLISECONDS);

            try {
                PsiFile targetFile = targetFileRef.get();
                if (targetFile != null) {
                    // Inspect single file
                    collectProblemsFromFile(project, profile, targetFile, inspectionNames, minSeverityLevel, maxProblems, problems, indicator, timedOut);
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
                        if (timedOut[0]) break;

                        // Get PsiFile in a small read action
                        PsiFile psiFile = ReadAction.compute(() -> psiManager.findFile(file));
                        if (psiFile != null) {
                            collectProblemsFromFile(project, profile, psiFile, inspectionNames, minSeverityLevel, maxProblems, problems, indicator, timedOut);
                        }
                    }
                }
            } catch (ProcessCanceledException e) {
                timedOut[0] = true;
                throw e;
            } finally {
                timeoutFuture.cancel(false);
                scheduler.shutdown();
            }

            // Limit results
            boolean truncated = problems.size() > maxProblems;
            List<InspectionProblem> resultProblems = truncated
                    ? new ArrayList<>(problems.subList(0, maxProblems))
                    : new ArrayList<>(problems);

            // Count by severity
            int errors = 0, warnings = 0, weakWarnings = 0, infos = 0;
            for (InspectionProblem p : resultProblems) {
                switch (p.severity()) {
                    case "ERROR" -> errors++;
                    case "WARNING" -> warnings++;
                    case "WEAK_WARNING" -> weakWarnings++;
                    default -> infos++;
                }
            }

            // Group by file and sort by severity
            Map<String, List<InspectionProblem>> problemsByFile = new LinkedHashMap<>();
            for (InspectionProblem p : resultProblems) {
                problemsByFile.computeIfAbsent(p.filePath(), k -> new ArrayList<>()).add(p);
            }

            // Sort problems within each file by severity (ERROR first, then WARNING, etc.)
            String projectPathPrefix = projectPath.endsWith("/") ? projectPath : projectPath + "/";
            List<FileProblems> groupedProblems = new ArrayList<>();
            for (Map.Entry<String, List<InspectionProblem>> entry : problemsByFile.entrySet()) {
                List<InspectionProblem> fileProblems = entry.getValue();
                fileProblems.sort(Comparator.comparingInt((InspectionProblem p) -> -getSeverityLevel(p.severity())));

                List<Problem> compactProblems = fileProblems.stream()
                        .map(p -> new Problem(
                                p.message(),
                                p.severity(),
                                p.inspectionId(),
                                p.lineRange() != null ? p.lineRange().startLine() : 0
                        ))
                        .toList();

                // Convert to relative path
                String relativePath = entry.getKey();
                if (relativePath != null && relativePath.startsWith(projectPathPrefix)) {
                    relativePath = relativePath.substring(projectPathPrefix.length());
                }

                groupedProblems.add(new FileProblems(relativePath, compactProblems));
            }

            // Sort files: files with errors first, then by file path
            groupedProblems.sort(Comparator
                    .comparingInt((FileProblems f) -> -f.problems().stream()
                            .mapToInt(p -> getSeverityLevel(p.severity()))
                            .max().orElse(0))
                    .thenComparing(FileProblems::filePath));

            return successResult(new InspectionResponse(
                    resultProblems.size(),
                    errors,
                    warnings,
                    weakWarnings,
                    infos,
                    timedOut[0],
                    truncated,
                    groupedProblems
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
                                         ProgressIndicator indicator, boolean[] timedOut) {
        if (problems.size() >= maxProblems) return;
        if (timedOut[0]) return;

        // Collect tool wrappers in a read action
        List<LocalInspectionToolWrapper> localTools = new ArrayList<>();
        List<GlobalInspectionToolWrapper> globalSimpleTools = new ArrayList<>();
        Map<String, String> toolSeverityMap = new HashMap<>();

        ReadAction.run(() -> {
            for (InspectionToolWrapper<?, ?> wrapper : profile.getInspectionTools(psiFile)) {
                if (!profile.isToolEnabled(wrapper.getDisplayKey(), psiFile)) continue;
                if (!matchesInspectionNames(wrapper, inspectionNames)) continue;

                var displayKey = wrapper.getDisplayKey();
                if (displayKey == null) continue;

                HighlightDisplayLevel level = profile.getErrorLevel(displayKey, psiFile);
                if (level == HighlightDisplayLevel.DO_NOT_SHOW) continue;

                String severityStr = getSeverityStringFromLevel(level);
                if (getSeverityLevel(severityStr) < minSeverityLevel) continue;

                toolSeverityMap.put(wrapper.getID(), severityStr);

                if (wrapper instanceof LocalInspectionToolWrapper localWrapper) {
                    localTools.add(localWrapper);
                } else if (wrapper instanceof GlobalInspectionToolWrapper globalWrapper) {
                    GlobalInspectionTool tool = globalWrapper.getTool();
                    if (tool.isGlobalSimpleInspectionTool()) {
                        globalSimpleTools.add(globalWrapper);
                    } else {
                        // Non-simple global tools may have a shared local inspection tool
                        LocalInspectionToolWrapper sharedLocalTool = globalWrapper.getSharedLocalInspectionToolWrapper();
                        if (sharedLocalTool != null) {
                            localTools.add(sharedLocalTool);
                            toolSeverityMap.put(sharedLocalTool.getID(), severityStr);
                        }
                    }
                }
            }
        });

        if (timedOut[0]) return;

        // Run local inspections (uses read action internally)
        if (!localTools.isEmpty()) {
            runLocalInspections(localTools, psiFile, toolSeverityMap, maxProblems, problems, indicator, timedOut);
        }

        // Run global simple inspections
        if (!globalSimpleTools.isEmpty() && !timedOut[0]) {
            runGlobalSimpleInspections(project, globalSimpleTools, psiFile, toolSeverityMap, maxProblems, problems, indicator, timedOut);
        }
    }

    private void runLocalInspections(List<LocalInspectionToolWrapper> toolWrappers,
                                     PsiFile psiFile,
                                     Map<String, String> toolSeverityMap, int maxProblems,
                                     List<InspectionProblem> problems,
                                     ProgressIndicator indicator, boolean[] timedOut) {
        try {
            if (timedOut[0]) return;

            // Run inspectEx with the cancellable indicator so timeout can interrupt it
            Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> results =
                    ProgressManager.getInstance().runProcess(() ->
                            ReadAction.compute(() ->
                                    InspectionEngine.inspectEx(toolWrappers, psiFile, psiFile.getTextRange(),
                                            psiFile.getTextRange(), false, false, true,
                                            indicator, (wrapper, descriptor) -> true)
                            ), indicator);

            // Process results outside of read action where possible
            for (var entry : results.entrySet()) {
                if (problems.size() >= maxProblems || timedOut[0]) break;

                LocalInspectionToolWrapper toolWrapper = entry.getKey();
                String severity = toolSeverityMap.get(toolWrapper.getID());
                if (severity == null) continue;

                for (ProblemDescriptor descriptor : entry.getValue()) {
                    if (problems.size() >= maxProblems) break;

                    // Create problem in read action (needs PSI access)
                    InspectionProblem problem = ReadAction.compute(() -> createProblem(psiFile, descriptor, toolWrapper, severity));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
            }
        } catch (ProcessCanceledException e) {
            timedOut[0] = true;
            throw e;
        } catch (Exception e) {
            LOG.debug("Local inspection failed for file " + psiFile.getName() + ": " + e.getMessage());
        }
    }

    private void runGlobalSimpleInspections(Project project,
                                            List<GlobalInspectionToolWrapper> toolWrappers,
                                            PsiFile psiFile,
                                            Map<String, String> toolSeverityMap, int maxProblems,
                                            List<InspectionProblem> problems,
                                            ProgressIndicator indicator, boolean[] timedOut) {
        InspectionManager inspectionManager = InspectionManager.getInstance(project);

        for (GlobalInspectionToolWrapper toolWrapper : toolWrappers) {
            if (problems.size() >= maxProblems || timedOut[0]) break;

            try {
                GlobalInspectionTool tool = toolWrapper.getTool();
                String severity = toolSeverityMap.get(toolWrapper.getID());
                if (severity == null) continue;

                // Run checkFile with the cancellable indicator
                List<ProblemDescriptor> descriptors = ProgressManager.getInstance().runProcess(() ->
                        ReadAction.compute(() -> {
                            ProblemsHolder holder = new ProblemsHolder(inspectionManager, psiFile, false);
                            ProblemDescriptionsProcessor processor = new SimpleProblemDescriptionsProcessor();
                            tool.checkFile(psiFile, inspectionManager, holder, createSimpleGlobalContext(project), processor);
                            return holder.getResults();
                        }), indicator);

                // Process results
                for (ProblemDescriptor descriptor : descriptors) {
                    if (problems.size() >= maxProblems) break;

                    InspectionProblem problem = ReadAction.compute(() -> createProblem(psiFile, descriptor, toolWrapper, severity));
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
            } catch (ProcessCanceledException e) {
                timedOut[0] = true;
                throw e;
            } catch (Exception e) {
                LOG.debug("Global simple inspection failed for " + toolWrapper.getDisplayName() + ": " + e.getMessage());
            }
        }
    }

    private GlobalInspectionContextBase createSimpleGlobalContext(Project project) {
        return new GlobalInspectionContextBase(project) {
            @Override
            protected void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
                // No-op - we run tools manually
            }
        };
    }

    private boolean matchesInspectionNames(InspectionToolWrapper<?, ?> toolWrapper, List<String> inspectionNames) {
        if (inspectionNames == null || inspectionNames.isEmpty()) return true;
        String shortName = toolWrapper.getShortName();
        String alternativeId = toolWrapper.getTool().getAlternativeID();
        boolean matched = inspectionNames.stream().anyMatch(name ->
                shortName.equals(name) || (alternativeId != null && alternativeId.equals(name)));
        if (matched) return true;

        // For global tools, also check the shared local tool's alternative ID
        if (toolWrapper instanceof GlobalInspectionToolWrapper globalWrapper) {
            LocalInspectionToolWrapper sharedLocal = globalWrapper.getSharedLocalInspectionToolWrapper();
            if (sharedLocal != null) {
                String sharedAlternativeId = sharedLocal.getTool().getAlternativeID();
                if (sharedAlternativeId != null) {
                    return inspectionNames.stream().anyMatch(sharedAlternativeId::equals);
                }
            }
        }
        return false;
    }

    private InspectionProblem createProblem(PsiFile psiFile, ProblemDescriptor descriptor,
                                            InspectionToolWrapper<?, ?> toolWrapper, String severity) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return null;
        }

        String filePath = psiFile.getVirtualFile() != null ? psiFile.getVirtualFile().getPath() : null;
        String message = descriptor.getDescriptionTemplate();
        String alternativeId = toolWrapper.getTool().getAlternativeID();
        String inspectionId = alternativeId != null ? alternativeId : toolWrapper.getShortName();

        LineRange lineRange = getLineRange(element);

        return new InspectionProblem(
                filePath,
                message,
                severity,
                inspectionId,
                lineRange
        );
    }

    private String getSeverityStringFromLevel(HighlightDisplayLevel level) {
        if (level == HighlightDisplayLevel.ERROR || level == HighlightDisplayLevel.NON_SWITCHABLE_ERROR) return "ERROR";
        if (level == HighlightDisplayLevel.WARNING || level == HighlightDisplayLevel.NON_SWITCHABLE_WARNING) return "WARNING";
        if (level == HighlightDisplayLevel.WEAK_WARNING) return "WEAK_WARNING";
        return "INFO";
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
            boolean truncated,
            List<FileProblems> files
    ) {}

    public record FileProblems(
            String filePath,
            List<Problem> problems
    ) {}

    public record Problem(
            String message,
            String severity,
            String inspectionId,
            int line
    ) {}

    // Internal record for collecting problems before grouping
    private record InspectionProblem(
            String filePath,
            String message,
            String severity,
            String inspectionId,
            LineRange lineRange
    ) {}
}