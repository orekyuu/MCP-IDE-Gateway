package net.orekyuu.intellijmcp.tools;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that retrieves the structure of a file (classes, methods, fields, etc.)
 * in a language-agnostic way using IntelliJ's StructureView API.
 * Accepts an absolute file path and automatically resolves the project context.
 */
public class FileStructureTool extends AbstractMcpTool<FileStructureTool.FileStructureResponse> {

    private static final Logger LOG = Logger.getInstance(FileStructureTool.class);

    private static final Arg<Path> FILE_PATH =
            Arg.absolutePath("filePath", "Absolute path to the file to get the structure for");

    @Override
    public String getDescription() {
        return "Get the structure of a file (classes, methods, fields, etc.) in a language-agnostic way using IntelliJ's StructureView API. Accepts an absolute file path.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(FILE_PATH);
    }

    @Override
    public Result<ErrorResponse, FileStructureResponse> execute(Map<String, Object> arguments) {
        return Args.validate(arguments, FILE_PATH)
                .mapN(filePath -> {
                    try {
                        return runReadActionWithResult(() -> {
                            VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByNioPath(filePath);
                            if (virtualFile == null) {
                                return errorResult("Error: File not found: " + filePath);
                            }
                            if (virtualFile.isDirectory()) {
                                return errorResult("Error: Path is a directory, not a file: " + filePath);
                            }

                            Project project = resolveProject(virtualFile);
                            if (project == null) {
                                return errorResult("Error: No open project found to parse the file");
                            }

                            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                            if (psiFile == null) {
                                return errorResult("Error: Cannot parse file: " + filePath);
                            }

                            var builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile);
                            if (!(builder instanceof TreeBasedStructureViewBuilder treeBuilder)) {
                                return successResult(new FileStructureResponse(filePath.toString(), List.of()));
                            }

                            StructureViewModel model = treeBuilder.createStructureViewModel(null);
                            try {
                                List<StructureNode> nodes = collectNodes(model.getRoot().getChildren());
                                return successResult(new FileStructureResponse(filePath.toString(), nodes));
                            } finally {
                                Disposer.dispose(model);
                            }
                        });
                    } catch (Exception e) {
                        LOG.error("Error in get_file_structure tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                })
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private Project resolveProject(VirtualFile virtualFile) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
        if (project != null) {
            return project;
        }
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        return openProjects.length > 0 ? openProjects[0] : null;
    }

    private List<StructureNode> collectNodes(TreeElement[] elements) {
        List<StructureNode> result = new ArrayList<>();
        for (TreeElement element : elements) {
            ItemPresentation pres = element.getPresentation();
            result.add(new StructureNode(
                    pres.getPresentableText(),
                    pres.getLocationString(),
                    collectNodes(element.getChildren())
            ));
        }
        return result;
    }

    public record FileStructureResponse(
            String filePath,
            List<StructureNode> structure
    ) {}

    public record StructureNode(
            String name,
            String location,
            List<StructureNode> children
    ) {}
}
