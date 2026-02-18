package net.orekyuu.intellijmcp.tools;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.TasksToRun;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    public void testMultipleGradleTestTasksProducesSingleConfigurationPerProducer() throws Exception {
        createProjectConfig(MULTI_TASK_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        // ConfigurationContext.getConfigurationsFromContext returns one config per producer,
        // so even with multiple test tasks, only one Gradle configuration is produced
        var configs = getConfigurationsForFile();

        assertThat(configs).isNotNull().isNotEmpty();

        long gradleConfigCount = configs.stream()
                .filter(c -> c.getConfigurationSettings().getType().getDisplayName().equals("Gradle"))
                .count();

        // IntelliJ's ConfigurationContext produces one configuration per producer type,
        // so we get exactly 1 Gradle config even with multiple test tasks
        assertThat(gradleConfigCount).isEqualTo(1);
    }
}
