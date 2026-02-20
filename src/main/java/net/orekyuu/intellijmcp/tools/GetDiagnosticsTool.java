package net.orekyuu.intellijmcp.tools;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * MCP tool that retrieves all diagnostics (errors and warnings) in the project.
 */
public class GetDiagnosticsTool extends AbstractProjectMcpTool<GetDiagnosticsTool.GetDiagnosticsResponse> {

    private static final Logger LOG = Logger.getInstance(GetDiagnosticsTool.class);

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Boolean> ERRORS_ONLY =
            Arg.bool("errorsOnly", "Whether to return only errors, excluding warnings").optional(false);

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
        return Args.schema(PROJECT, ERRORS_ONLY);
    }

    @Override
    public Result<ErrorResponse, GetDiagnosticsResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, ERRORS_ONLY)
                .mapN((project, errorsOnly) -> runReadActionWithResult(() -> {
                    try {
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
                                    if (!file.isDirectory() && isSourceFile(project, file) && !processedFiles.contains(file)) {
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
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private boolean isSourceFile(Project project, VirtualFile file) {
        return ProjectFileIndex.getInstance(project).isInSourceContent(file);
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
