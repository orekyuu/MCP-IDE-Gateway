package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class GetProjectDependenciesToolTest extends HeavyPlatformTestCase {

    private static final AtomicInteger ID = new AtomicInteger();

    private final List<Module> createdModules = new ArrayList<>();
    private final List<LibraryRef> createdLibraries = new ArrayList<>();

    private GetProjectDependenciesTool tool;
    private String prefix;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetProjectDependenciesTool();
        prefix = "mcp-deps-" + ID.incrementAndGet();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (LibraryRef ref : createdLibraries) {
                if (!ref.module().isDisposed()) {
                    PsiTestUtil.removeLibrary(ref.module(), ref.library());
                }
            }
            createdLibraries.clear();
            TestProjectBuilder.removeModules(getProject(), createdModules);
            createdModules.clear();
        } finally {
            super.tearDown();
        }
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of());
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of("projectPath", "/nonexistent/project/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    public void testDependenciesListIsEmptyWhenModuleHasNoDependencies() {
        Module isolated = createModule("isolated", "modules/isolated");

        var response = executeForProject(Map.of(
                "projectPath", getProject().getBasePath(),
                "moduleName", isolated.getName()
        ));

        assertThat(response.dependencies()).isEmpty();
    }

    public void testCollectsModuleDependenciesIncludingNestedModules() {
        Module shared = createModule("shared", "modules/shared");
        Module feature = createModule("feature-payments", "modules/features/payments");
        Module app = createModule("app", "modules/app");

        ModuleRootModificationUtil.addDependency(app, shared);
        ModuleRootModificationUtil.addDependency(app, feature);
        ModuleRootModificationUtil.addDependency(shared, feature);

        var response = executeForProject(Map.of(
                "projectPath", getProject().getBasePath(),
                "moduleName", app.getName()
        ));

        assertThat(response.dependencies())
                .extracting(GetProjectDependenciesTool.DependencyInfo::name)
                .contains(shared.getName(), feature.getName());
    }

    public void testFiltersByModuleAndIncludesLibraries() {
        Module core = createModule("core", "modules/core");
        Module consumer = createModule("consumer", "modules/consumer");
        ModuleRootModificationUtil.addDependency(consumer, core);

        String libraryName = prefix + "-json-lib";
        Library library = TestProjectBuilder.addProjectLibrary(getProject(), consumer, libraryName, "libs/json");
        createdLibraries.add(new LibraryRef(consumer, library));

        var response = executeForProject(Map.of(
                "projectPath", getProject().getBasePath(),
                "moduleName", consumer.getName()
        ));

        assertThat(response.dependencies())
                .extracting(GetProjectDependenciesTool.DependencyInfo::name)
                .containsExactlyInAnyOrder(core.getName(), libraryName);
    }

    private Module createModule(String name, String relativeDir) {
        Module module = TestProjectBuilder.createModule(getProject(), prefix + "-" + name, relativeDir);
        createdModules.add(module);
        return module;
    }

    private GetProjectDependenciesTool.GetProjectDependenciesResponse executeForProject(Map<String, Object> args) {
        var result = tool.execute(args);
        return McpToolResultAssert.assertThat(result).getSuccessResponse();
    }

    private record LibraryRef(Module module, Library library) {}
}
