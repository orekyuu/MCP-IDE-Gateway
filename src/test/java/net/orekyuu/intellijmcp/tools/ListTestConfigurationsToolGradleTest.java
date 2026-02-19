package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ListTestConfigurationsToolGradleTest extends ExternalSystemImportingTestCase {

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

    public void testExecuteListsFileConfigurations() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        String filePath = "project/src/test/java/com/example/MyTest.java";

        ListTestConfigurationsTool tool = new ListTestConfigurationsTool();
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", filePath
        ));

        McpToolResultAssert.assertThat(result).isSuccess();
        var response = (ListTestConfigurationsTool.ListTestConfigurationsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.fileConfigurations()).isNotEmpty();
        assertThat(response.fileConfigurations())
                .anyMatch(c -> c.type().equals("Gradle"));
    }

    public void testExecuteListsTestConfigurations() throws Exception {
        createProjectConfig(SIMPLE_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        String filePath = "project/src/test/java/com/example/MyTest.java";

        ListTestConfigurationsTool tool = new ListTestConfigurationsTool();
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", filePath
        ));

        McpToolResultAssert.assertThat(result).isSuccess();
        var response = (ListTestConfigurationsTool.ListTestConfigurationsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.testConfigurations()).isNotEmpty();
        assertThat(response.testConfigurations())
                .anyMatch(entry -> entry.testName().equals("testHello"));
    }

    public void testExecuteWithMultipleTasksListsConfigurations() throws Exception {
        createProjectConfig(MULTI_TASK_BUILD_GRADLE);
        createProjectSubFile("src/test/java/com/example/MyTest.java", TEST_CLASS_CONTENT);
        importProject();

        String projectPath = myProject.getBasePath();
        assertThat(projectPath).isNotNull();
        String filePath = "project/src/test/java/com/example/MyTest.java";

        ListTestConfigurationsTool tool = new ListTestConfigurationsTool();
        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", filePath
        ));

        McpToolResultAssert.assertThat(result).isSuccess();
        var response = (ListTestConfigurationsTool.ListTestConfigurationsResponse)
                McpToolResultAssert.assertThat(result).getSuccessResponse();

        // File-level configurations should include Gradle
        assertThat(response.fileConfigurations()).isNotEmpty();
        assertThat(response.fileConfigurations())
                .anyMatch(c -> c.type().equals("Gradle"));

        // testHello should have configurations for both 'test' and 'integrationTest' tasks
        var testHelloEntry = response.testConfigurations().stream()
                .filter(entry -> entry.testName().equals("testHello"))
                .findFirst();
        assertThat(testHelloEntry).isPresent();

        var testHelloConfigs = testHelloEntry.get().configurations();
        System.out.println("testHello configurations: " + testHelloConfigs);
        assertThat(testHelloConfigs)
                .as("testHello should have configurations for both test and integrationTest tasks")
                .hasSizeGreaterThanOrEqualTo(2);
    }
}
