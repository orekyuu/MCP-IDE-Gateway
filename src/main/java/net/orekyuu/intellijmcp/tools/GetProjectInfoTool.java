package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * MCP tool that returns project structure information including modules, SDKs, source roots, and dependencies.
 */
public class GetProjectInfoTool extends AbstractMcpTool<GetProjectInfoTool.GetProjectInfoResponse> {

    private static final Logger LOG = Logger.getInstance(GetProjectInfoTool.class);

    @Override
    public String getName() {
        return "get_project_info";
    }

    @Override
    public String getDescription() {
        return "Get project structure information including modules, SDKs, source roots, and dependencies";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return JsonSchemaBuilder.object()
                .requiredString("projectPath", "Absolute path to the project root directory")
                .build();
    }

    @Override
    public Result<ErrorResponse, GetProjectInfoResponse> execute(Map<String, Object> arguments) {
        return runReadActionWithResult(() -> {
            try {
                String projectPath;
                try {
                    projectPath = getRequiredStringArg(arguments, "projectPath");
                } catch (IllegalArgumentException e) {
                    return errorResult("Error: " + e.getMessage());
                }

                Optional<Project> projectOpt = findProjectByPath(projectPath);
                if (projectOpt.isEmpty()) {
                    return errorResult("Error: Project not found at path: " + projectPath);
                }
                Project project = projectOpt.get();
                String basePath = project.getBasePath();

                Module[] modules = ModuleManager.getInstance(project).getModules();
                List<ModuleInfo> moduleInfos = new ArrayList<>();

                for (Module module : modules) {
                    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

                    // SDK
                    SdkInfo sdkInfo = null;
                    Sdk sdk = rootManager.getSdk();
                    if (sdk != null) {
                        sdkInfo = new SdkInfo(
                                sdk.getName(),
                                sdk.getVersionString(),
                                sdk.getSdkType().getName()
                        );
                    }

                    // Source roots
                    List<SourceRootInfo> sourceRoots = new ArrayList<>();
                    for (ContentEntry contentEntry : rootManager.getContentEntries()) {
                        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
                            VirtualFile file = sourceFolder.getFile();
                            if (file == null) continue;

                            String path = toRelativePath(file.getPath(), basePath);
                            String type = resolveSourceRootType(sourceFolder);
                            sourceRoots.add(new SourceRootInfo(path, type));
                        }
                    }

                    // Module dependencies
                    List<String> moduleDependencies = new ArrayList<>();
                    for (Module dep : rootManager.getDependencies()) {
                        moduleDependencies.add(dep.getName());
                    }

                    // Library dependencies
                    List<String> libraries = new ArrayList<>();
                    OrderEnumerator.orderEntries(module)
                            .librariesOnly()
                            .forEachLibrary(library -> {
                                String name = library.getName();
                                if (name != null) {
                                    libraries.add(name);
                                }
                                return true;
                            });

                    moduleInfos.add(new ModuleInfo(
                            module.getName(),
                            sdkInfo,
                            sourceRoots,
                            moduleDependencies,
                            libraries
                    ));
                }

                return successResult(new GetProjectInfoResponse(project.getName(), moduleInfos));

            } catch (Exception e) {
                LOG.error("Error in get_project_info tool", e);
                return errorResult("Error: " + e.getMessage());
            }
        });
    }

    private String resolveSourceRootType(SourceFolder sourceFolder) {
        boolean isTest = sourceFolder.isTestSource();
        boolean isResource = sourceFolder.getRootType().getClass().getSimpleName().toLowerCase().contains("resource");
        if (isResource) {
            return isTest ? "test-resource" : "resource";
        }
        return isTest ? "test-source" : "source";
    }

    private String toRelativePath(String absolutePath, String basePath) {
        if (basePath != null && absolutePath.startsWith(basePath)) {
            String relative = absolutePath.substring(basePath.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return relative.isEmpty() ? "." : relative;
        }
        return absolutePath;
    }

    public record GetProjectInfoResponse(
            String projectName,
            List<ModuleInfo> modules
    ) {}

    public record ModuleInfo(
            String name,
            SdkInfo sdk,
            List<SourceRootInfo> sourceRoots,
            List<String> moduleDependencies,
            List<String> libraries
    ) {}

    public record SdkInfo(
            String name,
            String version,
            String type
    ) {}

    public record SourceRootInfo(
            String path,
            String type
    ) {}
}
