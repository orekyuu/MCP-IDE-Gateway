package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.TasksToRun;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RunTestToolGradleTest extends ExternalSystemImportingTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected String getTestsTempDir() {
        return "tmp";
    }

    @Override
    protected String getExternalSystemConfigFileName() {
        return "build.gradle";
    }

    @Override
    protected ExternalProjectSettings getCurrentExternalProjectSettings() {
        GradleProjectSettings settings = new GradleProjectSettings();
        settings.setGradleJvm("#JAVA_HOME");
        return settings;
    }

    @Override
    protected ProjectSystemId getExternalSystemId() {
        return GradleConstants.SYSTEM_ID;
    }

    @Override
    protected void setUpInWriteAction() throws Exception {
        String basePath = myProject.getBasePath();
        assertThat(basePath).as("Project base path").isNotNull();
        Path projectRootPath = Path.of(basePath).resolve("project");
        Files.createDirectories(projectRootPath);
        myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectRootPath);
    }

    private static final String SIMPLE_BUILD_GRADLE = """
            plugins {
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
            """;

    private static final String MULTI_TASK_BUILD_GRADLE = """
            plugins {
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }

            tasks.create('integrationTest', Test) {
                testClassesDirs = sourceSets.test.output.classesDirs
                classpath = sourceSets.test.runtimeClasspath
            }
            """;

    private static final String TEST_CLASS_CONTENT = """
            package com.example;
            import org.junit.Test;
            public class MyTest {
                @Test
                public void testHello() {
                    System.out.println("hello");
                }
            }
            """;

    private static final String JUNIT5_BUILD_GRADLE = """
            plugins {
                id 'java'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
            }
            """;

    private static final String NESTED_TEST_CONTENT = """
            package com.example;
            import org.junit.jupiter.api.Nested;
            import org.junit.jupiter.api.Test;
            public class OuterTest {
                @Test
                void testOuter() {
                    System.out.println("outer");
                }
                @Nested
                class InnerTest {
                    @Test
                    void testInner() {
                        System.out.println("inner");
                    }
                }
            }
            """;

    private List<ConfigurationFromContext> getConfigurationsForFile() {
        String projectPath = myProject.getBasePath();
        assertThat(projectPath).as("Project base path").isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).as("VirtualFile for test file").isNotNull();

        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vf);
            assertThat(psiFile).as("PsiFile for test file").isNotNull();
            ConfigurationContext context = new ConfigurationContext(psiFile);
            return context.getConfigurationsFromContext();
        });
    }

    public void testFindGradleRunConfigurationForTestFile() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        var configs = getConfigurationsForFile();

        assertThat(configs).isNotNull().isNotEmpty();
        assertThat(configs).anyMatch(c -> c.getConfigurationSettings().getType().getDisplayName().equals("Gradle"));
    }

    public void testFindGradleRunConfigurationForTestMethod() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).as("Project base path").isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        var configs = ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vf);
            assertThat(psiFile).isNotNull();
            var namedElements = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiNamedElement.class);
            for (var element : namedElements) {
                if ("testHello".equals(element.getName())) {
                    ConfigurationContext context = new ConfigurationContext(element);
                    var result = context.getConfigurationsFromContext();
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
            }
            return List.<ConfigurationFromContext>of();
        });

        assertThat(configs).isNotEmpty();
        assertThat(configs).anyMatch(c -> c.getConfigurationSettings().getType().getDisplayName().equals("Gradle"));
    }

    public void testMultipleGradleTestTasksDetected() throws Exception {
        createProjectConfig(MULTI_TASK_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        // GradleTestRunConfigurationProducer.findAllTestsTaskToRun detects multiple tasks
        String basePath = myProject.getBasePath();
        assertThat(basePath).as("Project base path").isNotNull();
        Path resolved = Path.of(basePath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        var testsToRun = GradleTestRunConfigurationProducer.findAllTestsTaskToRun(vf, myProject);
        assertThat(testsToRun).hasSizeGreaterThan(1);

        List<String> taskNames = testsToRun.stream()
                .map(TasksToRun::getTestName)
                .toList();
        System.out.println("Detected test tasks: " + taskNames);
        assertThat(taskNames).contains("test", "integrationTest");
    }

    /**
     * A LoggedErrorProcessor that suppresses AlreadyDisposedException.
     * RunTestTool.execute() uses invokeLater to run the test configuration,
     * which may execute after the project is disposed in the test environment.
     */
    private static final LoggedErrorProcessor SUPPRESS_DISPOSED = new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
            if (t instanceof AlreadyDisposedException) {
                return Action.NONE;
            }
            return Action.ALL;
        }
    };

    public void testExecuteWithSimpleGradleProject() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        String filePath = "project/src/test/java/com/example/MyTest.java";

        LoggedErrorProcessor.executeWith(SUPPRESS_DISPOSED, () -> {
            RunTestTool tool = new RunTestTool();
            var result = tool.execute(Map.of(
                    "projectPath", projectPath,
                    "filePath", filePath,
                    "timeoutSeconds", 1
            ));
            // The test may time out, but it should not return a "no configuration found" error.
            // A timeout response still means config was found and execution was attempted.
            McpToolResultAssert.assertThat(result).isSuccess();
            // Flush EDT to process any pending invokeLater callbacks while error processor is active
            UIUtil.dispatchAllInvocationEvents();
        });
    }

    public void testExecuteWithTestNameInGradleProject() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        String filePath = "project/src/test/java/com/example/MyTest.java";

        LoggedErrorProcessor.executeWith(SUPPRESS_DISPOSED, () -> {
            RunTestTool tool = new RunTestTool();
            var result = tool.execute(Map.of(
                    "projectPath", projectPath,
                    "filePath", filePath,
                    "testName", "testHello",
                    "timeoutSeconds", 1
            ));
            McpToolResultAssert.assertThat(result).isSuccess();
            UIUtil.dispatchAllInvocationEvents();
        });
    }

    /**
     * Verifies that specifying a test class file generates a Gradle configuration scoped
     * to that specific class only (via --tests in taskNames).
     * Without the filter, Gradle runs ALL tests in the module instead of just the class.
     */
    public void testGetConfigurationsForFileScopedToSpecificTestClass() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(vf));
        assertThat(psiFile).isNotNull();

        RunTestTool tool = new RunTestTool();
        var configs = tool.getConfigurationsForFile(psiFile);
        assertThat(configs).isNotEmpty();

        // IntelliJ stores the --tests filter in taskNames, not scriptParameters.
        // e.g. taskNames = [":test", "--tests", "com.example.MyTest"]
        List<RunnerAndConfigurationSettings> gradleConfigs = configs.stream()
                .filter(c -> c.getType().getDisplayName().equals("Gradle"))
                .toList();
        assertThat(gradleConfigs).isNotEmpty();

        gradleConfigs.forEach(c -> {
            if (c.getConfiguration() instanceof ExternalSystemRunConfiguration esrc) {
                String allTaskNames = String.join(" ", esrc.getSettings().getTaskNames());
                System.out.println("Config: " + c.getName() + ", taskNames: " + allTaskNames);
                assertThat(allTaskNames)
                        .as("Gradle config '%s' must contain --tests filter to run only MyTest, not all tests", c.getName())
                        .contains("com.example.MyTest");
            }
        });
    }

    /**
     * Verifies that a file with a @Nested inner test class generates a Gradle configuration
     * scoped to the outer class. Gradle also matches OuterTest$InnerTest from that filter.
     */
    public void testGetConfigurationsForFileWithNestedClassScopedToOuterClass() throws Exception {
        createProjectConfig(JUNIT5_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/OuterTest.java", NESTED_TEST_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/OuterTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(vf));
        assertThat(psiFile).isNotNull();

        RunTestTool tool = new RunTestTool();
        var configs = tool.getConfigurationsForFile(psiFile);
        assertThat(configs).isNotEmpty();

        List<RunnerAndConfigurationSettings> gradleConfigs = configs.stream()
                .filter(c -> c.getType().getDisplayName().equals("Gradle"))
                .toList();
        assertThat(gradleConfigs).isNotEmpty();

        // The config must scope to OuterTest; Gradle also matches OuterTest$InnerTest.
        gradleConfigs.forEach(c -> {
            if (c.getConfiguration() instanceof ExternalSystemRunConfiguration esrc) {
                String allTaskNames = String.join(" ", esrc.getSettings().getTaskNames());
                System.out.println("Config: " + c.getName() + ", taskNames: " + allTaskNames);
                assertThat(allTaskNames)
                        .as("Gradle config '%s' must contain --tests filter scoped to OuterTest", c.getName())
                        .contains("com.example.OuterTest");
            }
        });
    }

    /**
     * Bug regression test: GradleTestConfigurationExpander must preserve the --tests filter
     * in taskNames when expanding a class-scoped configuration for multiple Gradle test tasks.
     * Before the fix, setTaskNames(tasksToRun.getTasks()) stripped the filter, causing all
     * tests in the module to run instead of just the specified class.
     */
    public void testExpanderPreservesTestFilterForMultipleTasks() throws Exception {
        createProjectConfig(MULTI_TASK_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(vf));
        assertThat(psiFile).isNotNull();

        RunTestTool tool = new RunTestTool();
        var configs = tool.getConfigurationsForFile(psiFile);
        assertThat(configs).isNotEmpty();

        List<RunnerAndConfigurationSettings> gradleConfigs = configs.stream()
                .filter(c -> c.getType().getDisplayName().equals("Gradle"))
                .toList();
        assertThat(gradleConfigs)
                .as("Should have multiple Gradle configs after expansion (one per task)")
                .hasSizeGreaterThanOrEqualTo(2);

        // Every expanded config must preserve the --tests filter.
        // If the filter is stripped, Gradle runs all tests in the module.
        gradleConfigs.forEach(c -> {
            if (c.getConfiguration() instanceof ExternalSystemRunConfiguration esrc) {
                String allTaskNames = String.join(" ", esrc.getSettings().getTaskNames());
                System.out.println("Config: " + c.getName() + ", taskNames: " + allTaskNames);
                assertThat(allTaskNames)
                        .as("Expanded config '%s' must still contain --tests filter after task expansion", c.getName())
                        .contains("com.example.MyTest");
            }
        });
    }

    public void testMultipleGradleTestTasksExpandedByExpander() throws Exception {
        createProjectConfig(MULTI_TASK_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).as("Project base path").isNotNull();
        Path resolved = Path.of(projectPath).resolve("project/src/test/java/com/example/MyTest.java");
        VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(resolved);
        assertThat(vf).isNotNull();

        PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(vf));
        assertThat(psiFile).isNotNull();

        RunTestTool tool = new RunTestTool();
        var configs = tool.getConfigurationsForFile(psiFile);

        assertThat(configs).isNotNull().isNotEmpty();

        // GradleTestConfigurationExpander should expand into multiple configs (one per task)
        long gradleConfigCount = configs.stream()
                .filter(c -> c.getType().getDisplayName().equals("Gradle"))
                .count();
        assertThat(gradleConfigCount)
                .as("Should have multiple Gradle configs for test and integrationTest")
                .isGreaterThanOrEqualTo(2);

        List<String> configNames = configs.stream()
                .filter(c -> c.getType().getDisplayName().equals("Gradle"))
                .map(RunnerAndConfigurationSettings::getName)
                .toList();
        assertThat(configNames).anyMatch(name -> name.contains("test"));
        assertThat(configNames).anyMatch(name -> name.contains("integrationTest"));
    }
}
