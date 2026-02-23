package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.HeavyPlatformTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class GetProjectModulesToolTest extends HeavyPlatformTestCase {

    private static final AtomicInteger ID = new AtomicInteger();

    private final List<Module> createdModules = new ArrayList<>();

    private GetProjectModulesTool tool;
    private String prefix;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new GetProjectModulesTool();
        prefix = "mcp-modules-" + ID.incrementAndGet();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
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

    public void testReturnsEmptyRootListsWhenModuleHasNoSourceRoots() {
        Module empty = createModule("empty", "modules/empty");

        var response = executeForProject();
        var moduleInfo = findModule(response.modules(), empty.getName());

        assertThat(moduleInfo.sourceFolders()).isEmpty();
        assertThat(moduleInfo.resourceFolders()).isEmpty();
        assertThat(moduleInfo.testSourceFolders()).isEmpty();
        assertThat(moduleInfo.testResourceFolders()).isEmpty();
    }

    public void testReturnsMultipleModulesWithAllRootTypes() {
        Module app = createModule("app", "modules/app");
        TestProjectBuilder.addSourceFolder(getProject(), app, "modules/app/src/main/java");
        TestProjectBuilder.addResourceFolder(getProject(), app, "modules/app/src/main/resources");
        TestProjectBuilder.addTestSourceFolder(getProject(), app, "modules/app/src/test/java");
        TestProjectBuilder.addTestResourceFolder(getProject(), app, "modules/app/src/test/resources");

        Module shared = createModule("shared", "modules/shared");
        TestProjectBuilder.addSourceFolder(getProject(), shared, "modules/shared/src");

        var response = executeForProject();

        var appInfo = findModule(response.modules(), app.getName());
        assertThat(appInfo.path()).isEqualTo("modules/app");
        assertThat(appInfo.sourceFolders()).containsExactlyInAnyOrder("modules/app/src/main/java");
        assertThat(appInfo.resourceFolders()).containsExactly("modules/app/src/main/resources");
        assertThat(appInfo.testSourceFolders()).containsExactly("modules/app/src/test/java");
        assertThat(appInfo.testResourceFolders()).containsExactly("modules/app/src/test/resources");

        var sharedInfo = findModule(response.modules(), shared.getName());
        assertThat(sharedInfo.path()).isEqualTo("modules/shared");
        assertThat(sharedInfo.sourceFolders()).containsExactly("modules/shared/src");
        assertThat(sharedInfo.resourceFolders()).isEmpty();
        assertThat(sharedInfo.testSourceFolders()).isEmpty();
        assertThat(sharedInfo.testResourceFolders()).isEmpty();
    }

    public void testResolvesNestedModuleDirectories() {
        Module nested = createModule("feature-payments", "modules/features/payments");
        TestProjectBuilder.addSourceFolder(getProject(), nested, "modules/features/payments/src");

        var response = executeForProject();
        var nestedInfo = findModule(response.modules(), nested.getName());

        assertThat(nestedInfo.path()).isEqualTo("modules/features/payments");
        assertThat(nestedInfo.sourceFolders()).contains("modules/features/payments/src");
    }

    private Module createModule(String name, String relativeDir) {
        Module module = TestProjectBuilder.createModule(getProject(), prefix + "-" + name, relativeDir);
        createdModules.add(module);
        return module;
    }

    private GetProjectModulesTool.GetProjectModulesResponse executeForProject() {
        var result = tool.execute(Map.of("projectPath", getProject().getBasePath()));
        return McpToolResultAssert.assertThat(result).getSuccessResponse();
    }

    private GetProjectModulesTool.ModuleInfo findModule(List<GetProjectModulesTool.ModuleInfo> modules, String name) {
        return modules.stream()
                .filter(module -> module.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Module not found: " + name));
    }
}
