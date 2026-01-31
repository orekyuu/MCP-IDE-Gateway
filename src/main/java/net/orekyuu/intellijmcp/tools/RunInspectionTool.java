package net.orekyuu.intellijmcp.tools;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
    private static final int MAX_PROBLEMS = 100;

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
                .optionalString("filePath", "Absolute path to a specific file to inspect (optional, inspects entire project if not specified)")
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalString("inspectionName", "Name of a specific inspection to run (optional, runs all enabled inspections if not specified)")
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
                InspectionProfileImpl profile = (InspectionProfileImpl) InspectionProjectProfileManager
                        .getInstance(project).getCurrentProfile();

                // Run inspections
                List<InspectionProblem> problems = new ArrayList<>();

                if (targetFile != null) {
                    // Inspect single file
                    collectProblemsFromFile(project, profile, targetFile, inspectionName.orElse(null), problems);
                } else {
                    // Inspect project - get all source files
                    VirtualFile[] contentRoots = com.intellij.openapi.roots.ProjectRootManager
                            .getInstance(project).getContentSourceRoots();

                    for (VirtualFile root : contentRoots) {
                        if (problems.size() >= MAX_PROBLEMS) break;
                        collectProblemsFromDirectory(project, profile, root, inspectionName.orElse(null), problems);
                    }
                }

                // Limit results
                if (problems.size() > MAX_PROBLEMS) {
                    problems = problems.subList(0, MAX_PROBLEMS);
                }

                // Group by severity
                int errors = 0, warnings = 0, weakWarnings = 0, infos = 0;
                for (InspectionProblem p : problems) {
                    switch (p.severity()) {
                        case "ERROR" -> errors++;
                        case "WARNING" -> warnings++;
                        case "WEAK_WARNING" -> weakWarnings++;
                        default -> infos++;
                    }
                }

                return successResult(new InspectionResponse(
                        problems.size(),
                        errors,
                        warnings,
                        weakWarnings,
                        infos,
                        problems
                ));

            } catch (Exception e) {
                LOG.error("Error in run_inspection tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private void collectProblemsFromDirectory(Project project, InspectionProfileImpl profile,
                                               VirtualFile directory, String inspectionName,
                                               List<InspectionProblem> problems) {
        if (problems.size() >= MAX_PROBLEMS) return;

        for (VirtualFile child : directory.getChildren()) {
            if (problems.size() >= MAX_PROBLEMS) return;

            if (child.isDirectory()) {
                collectProblemsFromDirectory(project, profile, child, inspectionName, problems);
            } else if (isSourceFile(child)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile != null) {
                    collectProblemsFromFile(project, profile, psiFile, inspectionName, problems);
                }
            }
        }
    }

    private void collectProblemsFromFile(Project project, InspectionProfileImpl profile,
                                          PsiFile psiFile, String inspectionName,
                                          List<InspectionProblem> problems) {
        if (problems.size() >= MAX_PROBLEMS) return;

        InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(project);

        // Get all inspection tools
        List<InspectionToolWrapper<?, ?>> tools = profile.getInspectionTools(psiFile);

        for (InspectionToolWrapper<?, ?> toolWrapper : tools) {
            if (problems.size() >= MAX_PROBLEMS) break;

            // Filter by inspection name if specified
            if (inspectionName != null && !toolWrapper.getShortName().contains(inspectionName)
                    && !toolWrapper.getDisplayName().toLowerCase().contains(inspectionName.toLowerCase())) {
                continue;
            }

            // Skip disabled inspections
            if (!profile.isToolEnabled(toolWrapper.getDisplayKey(), psiFile)) {
                continue;
            }

            InspectionProfileEntry tool = toolWrapper.getTool();
            if (tool instanceof LocalInspectionTool localTool) {
                try {
                    ProblemsHolder holder = new ProblemsHolder(inspectionManager, psiFile, false);
                    PsiElementVisitor visitor = localTool.buildVisitor(holder, false);

                    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
                        @Override
                        public void visitElement(PsiElement element) {
                            element.accept(visitor);
                            super.visitElement(element);
                        }
                    });

                    for (ProblemDescriptor descriptor : holder.getResults()) {
                        if (problems.size() >= MAX_PROBLEMS) break;

                        InspectionProblem problem = createProblem(psiFile, descriptor, toolWrapper);
                        if (problem != null) {
                            problems.add(problem);
                        }
                    }
                } catch (Exception e) {
                    // Some inspections may fail, continue with others
                    LOG.debug("Inspection " + toolWrapper.getShortName() + " failed: " + e.getMessage());
                }
            }
        }
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

    private boolean isSourceFile(VirtualFile file) {
        String extension = file.getExtension();
        return extension != null && (
                extension.equals("java") ||
                extension.equals("kt") ||
                extension.equals("scala") ||
                extension.equals("groovy")
        );
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
