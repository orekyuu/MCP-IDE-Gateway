package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.vfs.LocalFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

class RunTestToolTest extends BaseMcpToolTest<RunTestTool> {

    @Override
    RunTestTool createTool() {
        return new RunTestTool();
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("filePath", "src/test/Foo.java"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of("projectPath", "/some/project"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "projectPath", "/nonexistent/project/path",
                "filePath", "src/test/Foo.java"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithPathTraversal() {
        var result = tool.execute(Map.of(
                "projectPath", "/some/project",
                "filePath", "../../etc/passwd"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Path is outside the project directory");
    }

    @Test
    void executeWithNonExistentFile() {
        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "projectPath", projectPath,
                "filePath", "src/test/java/NonExistent.java"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("File not found");
    }

    @Test
    void executeWithNoRunConfiguration() throws IOException {
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
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("No run configuration found");
    }

    @Test
    void executeWithMethodNameAndNoRunConfiguration() throws IOException {
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
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("No run configuration found");
    }

}
