package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

public class RunTestToolTest extends BasePlatformTestCase {

    private RunTestTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new RunTestTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("run_test");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("test");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("projectPath")
                .containsKey("filePath")
                .containsKey("methodName")
                .containsKey("configurationName")
                .containsKey("timeoutSeconds");
        assertThat(schema.required())
                .isNotNull()
                .contains("projectPath", "filePath")
                .doesNotContain("methodName", "configurationName", "timeoutSeconds");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("filePath", "src/test/Foo.java"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/project"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("filePath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "filePath", "src/test/Foo.java"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testExecuteWithPathTraversal() {
        var result = tool.execute(Map.of(
                "projectPath", "/some/project",
                "filePath", "../../etc/passwd"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("Path is outside the project directory");
    }

    public void testExecuteWithNonExistentFile() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "src/test/java/NonExistent.java"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("File not found");
    }

    public void testExecuteWithNoRunConfiguration() throws IOException {
        // BasePlatformTestCase doesn't have test runners, so no configuration should be found
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        Path filePath = Path.of(projectPath, "src", "PlainFile.java");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, """
                public class PlainFile {
                    public static void main(String[] args) {}
                }
                """);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "src/PlainFile.java"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("No run configuration found");
    }

    public void testExecuteWithMethodNameAndNoRunConfiguration() throws IOException {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        Path filePath = Path.of(projectPath, "src", "SomeTest.java");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, """
                public class SomeTest {
                    public void testSomething() {}
                }
                """);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath);

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "src/SomeTest.java",
                "methodName", "testSomething"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, Object>) result;
        assertThat(errorResult.message().message()).contains("No run configuration found");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("run_test");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }
}
