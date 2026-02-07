package net.orekyuu.intellijmcp.tools;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * MCP tool that retrieves all diagnostics (errors and warnings) in the project.
 */
public class GetDiagnosticsTool extends AbstractMcpTool<GetDiagnosticsTool.GetDiagnosticsResponse> {

    private static final Logger LOG = Logger.getInstance(GetDiagnosticsTool.class);

    @Override
    public String getName() {
        return "get_diagnostics";
    }

    @Override
    public String getDescription() {
        return "Get all diagnostics (errors and warnings) in the project";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .optionalBoolean("errorsOnly", "Whether to return only errors, excluding warnings (default: false)")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetDiagnosticsResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                String projectPath;
                try {
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: projectPath is required");
                }
                boolean errorsOnly = getBooleanArg(arguments, "errorsOnly").orElse(false);

                // Find project
                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();

                // Get problem files using WolfTheProblemSolver
                WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(project);

                List<FileDiagnostics> fileDiagnosticsList = new ArrayList<>();
                int totalErrors = 0;
                int totalWarnings = 0;

                // Get all source roots and scan for problems
                VirtualFile[] contentRoots = com.intellij.openapi.roots.ProjectRootManager
                        .getInstance(project).getContentSourceRoots();

                Set<VirtualFile> processedFiles = new HashSet<>();

                for (VirtualFile root : contentRoots) {
                    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                        @Override
                        public boolean visitFile(@NotNull VirtualFile file) {
                            if (!file.isDirectory() && isSourceFile(file) && !processedFiles.contains(file)) {
                                processedFiles.add(file);

                                if (problemSolver.isProblemFile(file)) {
                                    FileDiagnostics fileDiag = getFileDiagnostics(project, file, errorsOnly);
                                    if (fileDiag != null && !fileDiag.diagnostics().isEmpty()) {
                                        fileDiagnosticsList.add(fileDiag);
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }

                // Calculate totals
                for (FileDiagnostics fd : fileDiagnosticsList) {
                    for (DiagnosticInfo d : fd.diagnostics()) {
                        if ("error".equals(d.severity())) {
                            totalErrors++;
                        } else {
                            totalWarnings++;
                        }
                    }
                }

                return successResult(new GetDiagnosticsResponse(
                        totalErrors,
                        totalWarnings,
                        fileDiagnosticsList
                ));

            } catch (Exception e) {
                LOG.error("Error in get_diagnostics tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
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

    @SuppressWarnings("UnstableApiUsage")
    private FileDiagnostics getFileDiagnostics(Project project, VirtualFile file, boolean errorsOnly) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return null;
        }

        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) {
            return null;
        }

        List<HighlightInfo> highlights = DaemonCodeAnalyzerImpl.getHighlights(
                document, HighlightSeverity.WARNING, project);

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        for (HighlightInfo info : highlights) {
            if (info.getSeverity().compareTo(HighlightSeverity.WARNING) < 0) {
                continue; // Skip info and below
            }

            if (errorsOnly && info.getSeverity().compareTo(HighlightSeverity.ERROR) < 0) {
                continue;
            }

            String severity = info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0 ? "error" : "warning";
            String message = info.getDescription();

            if (message == null || message.isEmpty()) {
                continue;
            }

            int startLine = document.getLineNumber(info.getStartOffset()) + 1;
            int endLine = document.getLineNumber(info.getEndOffset()) + 1;
            LineRange lineRange = new LineRange(startLine, endLine);

            // Get the problematic code snippet
            int lineStartOffset = document.getLineStartOffset(startLine - 1);
            int lineEndOffset = document.getLineEndOffset(startLine - 1);
            String context = document.getText().substring(lineStartOffset, lineEndOffset).trim();

            diagnostics.add(new DiagnosticInfo(severity, message, lineRange, context));
        }

        if (diagnostics.isEmpty()) {
            return null;
        }

        return new FileDiagnostics(file.getPath(), diagnostics);
    }

    public record GetDiagnosticsResponse(
            int totalErrors,
            int totalWarnings,
            List<FileDiagnostics> files
    ) {}

    public record FileDiagnostics(
            String filePath,
            List<DiagnosticInfo> diagnostics
    ) {}

    public record DiagnosticInfo(
            String severity,
            String message,
            LineRange lineRange,
            String context
    ) {}
}
