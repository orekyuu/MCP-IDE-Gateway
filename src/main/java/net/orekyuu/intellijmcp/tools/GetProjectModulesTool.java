package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCP tool that returns module information such as directories and source roots.
 */
public class GetProjectModulesTool extends AbstractProjectMcpTool<GetProjectModulesTool.GetProjectModulesResponse> {

    private static final Logger LOG = Logger.getInstance(GetProjectModulesTool.class);

    private static final Arg<Project> PROJECT = Arg.project();

    @Override
    public String getDescription() {
        return "List all modules in a project with their source roots, resource roots, and directories. Use this to understand project structure before creating files, or to find where source files should be placed.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT);
    }

    @Override
    public Result<ErrorResponse, GetProjectModulesResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT)
                .mapN(project -> runReadActionWithResult(() -> {
                    try {
                        String basePath = project.getBasePath();
                        Module[] modules = ModuleManager.getInstance(project).getModules();
                        List<ModuleInfo> moduleInfos = new ArrayList<>();

                        for (Module module : modules) {
                            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
                            List<String> sourceFolders = new ArrayList<>();
                            List<String> resourceFolders = new ArrayList<>();
                            List<String> testSourceFolders = new ArrayList<>();
                            List<String> testResourceFolders = new ArrayList<>();

                            for (ContentEntry contentEntry : rootManager.getContentEntries()) {
                                for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
                                    VirtualFile file = sourceFolder.getFile();
                                    if (file == null) {
                                        continue;
                                    }
                                    String relativePath = toRelativePath(file.getPath(), basePath);
                                    if (isResourceRoot(sourceFolder)) {
                                        if (sourceFolder.isTestSource()) {
                                            testResourceFolders.add(relativePath);
                                        } else {
                                            resourceFolders.add(relativePath);
                                        }
                                    } else {
                                        if (sourceFolder.isTestSource()) {
                                            testSourceFolders.add(relativePath);
                                        } else {
                                            sourceFolders.add(relativePath);
                                        }
                                    }
                                }
                            }

                            moduleInfos.add(new ModuleInfo(
                                    module.getName(),
                                    toRelativePath(getModuleDirectoryPath(module), basePath),
                                    module.getModuleTypeName(),
                                    List.copyOf(sourceFolders),
                                    List.copyOf(resourceFolders),
                                    List.copyOf(testSourceFolders),
                                    List.copyOf(testResourceFolders)
                            ));
                        }

                        return successResult(new GetProjectModulesResponse(moduleInfos));
                    } catch (Exception e) {
                        LOG.error("Error in get_project_modules tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private String getModuleDirectoryPath(Module module) {
        String moduleFilePath = module.getModuleFilePath();
        if (moduleFilePath == null) {
            return null;
        }
        return PathUtilRt.getParentPath(moduleFilePath);
    }

    private boolean isResourceRoot(SourceFolder sourceFolder) {
        String typeName = sourceFolder.getRootType().getClass().getSimpleName();
        return typeName.toLowerCase(Locale.ROOT).contains("resource");
    }

    private String toRelativePath(String absolutePath, String basePath) {
        if (absolutePath == null) {
            return ".";
        }
        if (basePath == null || basePath.isBlank()) {
            return absolutePath;
        }
        try {
            Path absolute = Paths.get(absolutePath).normalize();
            Path base = Paths.get(basePath).normalize();
            if (absolute.startsWith(base)) {
                String relative = base.relativize(absolute).toString().replace('\\', '/');
                return relative.isEmpty() ? "." : relative;
            }
        } catch (InvalidPathException ignored) {
            // fall through and return original path
        }
        return absolutePath;
    }

    public record GetProjectModulesResponse(List<ModuleInfo> modules) {}

    public record ModuleInfo(
            String name,
            String path,
            String type,
            List<String> sourceFolders,
            List<String> resourceFolders,
            List<String> testSourceFolders,
            List<String> testResourceFolders
    ) {}
}
