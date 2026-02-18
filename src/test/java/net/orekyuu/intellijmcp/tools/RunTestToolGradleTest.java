package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class RunTestToolGradleTest extends ExternalSystemImportingTestCase {

    private RunTestTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new RunTestTool();
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

    public void testFindGradleRunConfigurationForTestFile() throws Exception {
        createProjectConfig("""
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'junit:junit:4.13.2'
                }
                """);

        createProjectSubFile("src/test/java/com/example/MyTest.java", """
                package com.example;
                import org.junit.Test;
                public class MyTest {
                    @Test
                    public void testHello() {
                        System.out.println("hello");
                    }
                }
                """);

        importProject();

        String projectPath = myProject.getBasePath();

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "project/src/test/java/com/example/MyTest.java"
        ));

        assertThat(result).isNotNull();
        // Gradle run configuration should be found (not an error about "No run configuration found")
        if (result instanceof McpTool.Result.ErrorResponse<?, ?> errorResult) {
            assertThat(((ErrorResponse) errorResult.message()).message())
                    .doesNotContain("No run configuration found");
        }
    }

    public void testFindGradleRunConfigurationForTestMethod() throws Exception {
        createProjectConfig("""
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'junit:junit:4.13.2'
                }
                """);

        createProjectSubFile("src/test/java/com/example/MyTest.java", """
                package com.example;
                import org.junit.Test;
                public class MyTest {
                    @Test
                    public void testHello() {
                        System.out.println("hello");
                    }
                }
                """);

        importProject();

        String projectPath = myProject.getBasePath();

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "project/src/test/java/com/example/MyTest.java",
                "methodName", "testHello"
        ));

        assertThat(result).isNotNull();
        // Gradle run configuration should be found (not an error about "No run configuration found")
        if (result instanceof McpTool.Result.ErrorResponse<?, ?> errorResult) {
            assertThat(((ErrorResponse) errorResult.message()).message())
                    .doesNotContain("No run configuration found");
        }
    }
}
