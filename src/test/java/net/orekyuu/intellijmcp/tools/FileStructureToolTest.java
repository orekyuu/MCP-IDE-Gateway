package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileStructureToolTest extends BaseMcpToolTest<FileStructureTool> {

    @Override
    @BeforeEach
    void setUp() throws Exception {
        IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
        TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
                factory.createLightFixtureBuilder(null, "test");
        IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
        // Use default temp dir fixture so files are on the real filesystem (required for findFileByNioPath)
        myFixture = factory.createCodeInsightFixture(fixture);
        myFixture.setUp();
        tool = createTool();
    }

    @Override
    FileStructureTool createTool() {
        return new FileStructureTool();
    }

    @Test
    void executeWithMissingFilePath() {
        var result = tool.execute(Map.of());
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeWithNonAbsolutePath() {
        var result = tool.execute(Map.of("filePath", "relative/path.java"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("filePath");
    }

    @Test
    void executeReturnsJavaStructure() {
        myFixture.configureByText("Main.java", """
                public class Main {
                    private String name;

                    public void greet() {
                        System.out.println("Hello");
                    }

                    public String getName() {
                        return name;
                    }
                }
                """);

        VirtualFile vf = myFixture.getFile().getVirtualFile();
        var result = tool.execute(Map.of("filePath", vf.getPath()));

        var response = McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.filePath()).isEqualTo(vf.getPath());
        assertThat(response.structure())
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new FileStructureTool.StructureNode("Main", null, List.of(
                                new FileStructureTool.StructureNode("name: String",    null, List.of()),
                                new FileStructureTool.StructureNode("greet(): void",   null, List.of()),
                                new FileStructureTool.StructureNode("getName(): String", null, List.of())
                        ))
                ));
    }

    @Test
    void executeReturnsJsonStructure() {
        myFixture.configureByText("config.json", """
                {
                    "name": "test",
                    "version": "1.0",
                    "number": 123,
                    "bool": false,
                    "null": null,
                    "obj": {
                      "child": {
                        "empty": {}
                      }
                    },
                    "array": [1, 2, 3],
                    "obj_array": [{"id": 1}, {"id": 2}]
                }
                """);

        VirtualFile vf = myFixture.getFile().getVirtualFile();
        var result = tool.execute(Map.of("filePath", vf.getPath()));

        var response = McpToolResultAssert.assertThat(result).getSuccessResponse();
        assertThat(response.filePath()).isEqualTo(vf.getPath());

        assertThat(response.structure())
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new FileStructureTool.StructureNode("name",    "\"test\"", List.of()),
                        new FileStructureTool.StructureNode("version", "\"1.0\"",  List.of()),
                        new FileStructureTool.StructureNode("number",  "123",      List.of()),
                        new FileStructureTool.StructureNode("bool",    "false",    List.of()),
                        new FileStructureTool.StructureNode("null",    "null",     List.of()),
                        new FileStructureTool.StructureNode("obj", null, List.of(
                                new FileStructureTool.StructureNode("child", null, List.of(
                                        new FileStructureTool.StructureNode("empty", null, List.of())
                                ))
                        )),
                        new FileStructureTool.StructureNode("array", null, List.of()),
                        new FileStructureTool.StructureNode("obj_array", null, List.of(
                                new FileStructureTool.StructureNode("object", null, List.of(
                                        new FileStructureTool.StructureNode("id", "1", List.of())
                                )),
                                new FileStructureTool.StructureNode("object", null, List.of(
                                        new FileStructureTool.StructureNode("id", "2", List.of())
                                ))
                        ))
                ));
    }
}
