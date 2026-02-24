package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import io.modelcontextprotocol.spec.McpSchema;
import net.orekyuu.intellijmcp.tools.validator.Arg;
import net.orekyuu.intellijmcp.tools.validator.Args;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MCP tool that lists module and library dependencies for a project or specific module.
 */
public class GetProjectDependenciesTool extends AbstractProjectMcpTool<GetProjectDependenciesTool.GetProjectDependenciesResponse> {

    private static final Logger LOG = Logger.getInstance(GetProjectDependenciesTool.class);

    private static final Arg<Project> PROJECT = Arg.project();
    private static final Arg<Optional<String>> MODULE_NAME = Arg.string("moduleName", "Module name to filter dependencies").optional();

    @Override
    public String getDescription() {
        return "List module and library dependencies for the entire project or a single module";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return Args.schema(PROJECT, MODULE_NAME);
    }

    @Override
    public Result<ErrorResponse, GetProjectDependenciesResponse> doExecute(Map<String, Object> arguments) {
        return Args.validate(arguments, PROJECT, MODULE_NAME)
                .mapN((project, moduleNameOpt) -> runReadActionWithResult(() -> {
                    try {
                        Module[] allModules = ModuleManager.getInstance(project).getModules();
                        List<Module> targetModules;
                        if (moduleNameOpt.isPresent()) {
                            targetModules = findTargetModule(allModules, moduleNameOpt.get());
                            if (targetModules.isEmpty()) {
                                return errorResult("Error: Module not found: " + moduleNameOpt.get());
                            }
                        } else {
                            targetModules = List.of(allModules);
                        }

                        Set<String> dependencyNames = new LinkedHashSet<>();
                        for (Module module : targetModules) {
                            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
                            for (Module dependency : rootManager.getDependencies()) {
                                dependencyNames.add(dependency.getName());
                            }
                            OrderEnumerator.orderEntries(module)
                                    .librariesOnly()
                                    .forEachLibrary(library -> {
                                        String name = library.getName();
                                        if (name != null) {
                                            dependencyNames.add(name);
                                        }
                                        return true;
                                    });
                        }

                        List<DependencyInfo> dependencies = dependencyNames.stream()
                                .map(DependencyInfo::new)
                                .toList();
                        return successResult(new GetProjectDependenciesResponse(dependencies));
                    } catch (Exception e) {
                        LOG.error("Error in get_project_dependencies tool", e);
                        return errorResult("Error: " + e.getMessage());
                    }
                }))
                .orElseErrors(errors -> errorResult("Error: " + Args.formatErrors(errors)));
    }

    private List<Module> findTargetModule(Module[] allModules, String moduleName) {
        List<Module> modules = new ArrayList<>();
        for (Module module : allModules) {
            if (module.getName().equals(moduleName)) {
                modules.add(module);
                break;
            }
        }
        return modules;
    }

    public record GetProjectDependenciesResponse(List<DependencyInfo> dependencies) {}

    public record DependencyInfo(String name) {}
}
