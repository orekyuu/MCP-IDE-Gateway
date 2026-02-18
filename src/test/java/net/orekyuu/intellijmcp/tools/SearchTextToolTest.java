package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

class SearchTextToolTest extends BaseMcpToolTest<SearchTextTool> {

    @Override
    SearchTextTool createTool() {
        return new SearchTextTool();
    }

    @Test
    void executeWithMissingSearchText() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("searchText");
    }

    @Test
    void executeWithMissingProjectPath() {
        var result = tool.execute(Map.of("searchText", "test"));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("projectPath");
    }

    @Test
    void executeWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "searchText", "test",
                "projectPath", "/nonexistent/project/path"
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Project not found at path");
    }

    @Test
    void executeWithInvalidRegex() {
        var result = tool.execute(Map.of(
                "searchText", "[invalid(regex",
                "projectPath", "/some/path",
                "useRegex", true
        ));
        McpToolResultAssert.assertThat(result).hasErrorMessageContaining("Invalid regular expression");
    }

    @Test
    void searchInProject() {
        myFixture.configureByText("TestFile.java", """
                public class TestFile {
                    private String searchableText = "hello world";

                    public void method() {
                        System.out.println("searchableText here");
                    }
                }
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());
        var result = tool.execute(Map.of(
                "searchText", "searchableText",
                "projectPath", projectPath
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.searchText()).isEqualTo("searchableText");
        assertThat(response.useRegex()).isFalse();
        assertThat(response.caseSensitive()).isFalse();
        assertThat(response.totalMatches()).isGreaterThanOrEqualTo(2);
        assertThat(response.matches()).isNotEmpty();
    }

    @Test
    void searchWithCaseSensitive() {
        myFixture.configureByText("CaseTest.java", """
                public class CaseTest {
                    String UPPER = "TEST";
                    String lower = "test";
                }
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "searchText", "TEST",
                "projectPath", projectPath,
                "caseSensitive", true
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.caseSensitive()).isTrue();
        assertThat(response.matches())
                .allMatch(match -> match.matchedText().equals("TEST"));
    }

    @Test
    void searchWithRegex() {
        myFixture.configureByText("RegexTest.java", """
                public class RegexTest {
                    int value1 = 100;
                    int value2 = 200;
                    int value3 = 300;
                }
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "searchText", "value\\d",
                "projectPath", projectPath,
                "useRegex", true
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.useRegex()).isTrue();
        assertThat(response.totalMatches()).isGreaterThanOrEqualTo(3);
        assertThat(response.matches())
                .extracting(SearchTextTool.SearchMatch::matchedText)
                .allMatch(text -> text.matches("value\\d"));
    }

    @Test
    void searchWithFilePattern() {
        myFixture.configureByText("SearchMe.java", "// target text in java");
        myFixture.configureByText("IgnoreMe.txt", "target text in txt");

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "searchText", "target text",
                "projectPath", projectPath,
                "filePattern", "*.java"
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.filePattern()).isEqualTo("*.java");
        assertThat(response.matches())
                .allMatch(match -> match.filePath().endsWith(".java"));
    }

    @Test
    void searchWithMaxResults() {
        StringBuilder content = new StringBuilder("public class ManyMatches {\n");
        for (int i = 0; i < 50; i++) {
            content.append("    String match").append(i).append(" = \"findme\";\n");
        }
        content.append("}");

        myFixture.configureByText("ManyMatches.java", content.toString());

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "searchText", "findme",
                "projectPath", projectPath,
                "maxResults", 10
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.totalMatches()).isEqualTo(10);
        assertThat(response.truncated()).isTrue();
        assertThat(response.matches()).hasSize(10);
    }

    @Test
    void searchMatchContainsLineInfo() {
        myFixture.configureByText("LineInfo.java", """
                line1
                line2 target
                line3
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        var result = tool.execute(Map.of(
                "searchText", "target",
                "projectPath", projectPath
        ));

        var response = McpToolResultAssert.<SearchTextTool.SearchTextResponse>assertThat(result).getSuccessResponse();

        assertThat(response.matches()).hasSize(1);
        var match = response.matches().getFirst();

        assertThat(match.line()).isEqualTo(2);
        assertThat(match.column()).isGreaterThan(0);
        assertThat(match.matchedText()).isEqualTo("target");
        assertThat(match.lineContent()).contains("target");
        assertThat(match.filePath()).endsWith("LineInfo.java");
    }
}
