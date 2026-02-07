package net.orekyuu.intellijmcp.tools;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

public class SearchTextToolTest extends BasePlatformTestCase {

    private SearchTextTool tool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tool = new SearchTextTool();
    }

    public void testGetName() {
        assertThat(tool.getName()).isEqualTo("search_text");
    }

    public void testGetDescription() {
        assertThat(tool.getDescription())
                .isNotNull()
                .containsIgnoringCase("search")
                .containsIgnoringCase("text");
    }

    public void testGetInputSchema() {
        McpSchema.JsonSchema schema = tool.getInputSchema();

        assertThat(schema).isNotNull();
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties())
                .isNotNull()
                .containsKey("searchText")
                .containsKey("projectPath")
                .containsKey("useRegex")
                .containsKey("caseSensitive")
                .containsKey("filePattern")
                .containsKey("maxResults");
        assertThat(schema.required())
                .isNotNull()
                .contains("searchText", "projectPath")
                .doesNotContain("useRegex", "caseSensitive", "filePattern", "maxResults");
    }

    public void testExecuteWithMissingSearchText() {
        var result = tool.execute(Map.of("projectPath", "/some/path"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        assertThat(errorResult.message().message()).contains("searchText");
    }

    public void testExecuteWithMissingProjectPath() {
        var result = tool.execute(Map.of("searchText", "test"));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        assertThat(errorResult.message().message()).contains("projectPath");
    }

    public void testExecuteWithNonExistentProject() {
        var result = tool.execute(Map.of(
                "searchText", "test",
                "projectPath", "/nonexistent/project/path"
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        assertThat(errorResult.message().message()).contains("Project not found at path");
    }

    public void testExecuteWithInvalidRegex() {
        var result = tool.execute(Map.of(
                "searchText", "[invalid(regex",
                "projectPath", "/some/path",
                "useRegex", true
        ));

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.ErrorResponse.class);

        var errorResult = (McpTool.Result.ErrorResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        assertThat(errorResult.message().message()).contains("Invalid regular expression");
    }

    public void testToSpecification() {
        var spec = tool.toSpecification();

        assertThat(spec).isNotNull();
        assertThat(spec.tool()).isNotNull();
        assertThat(spec.tool().name()).isEqualTo("search_text");
        assertThat(spec.tool().inputSchema()).isNotNull();
    }

    public void testSearchInProject() {
        // Create a test file with content
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

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);

        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.searchText()).isEqualTo("searchableText");
        assertThat(response.useRegex()).isFalse();
        assertThat(response.caseSensitive()).isFalse();
        assertThat(response.totalMatches()).isGreaterThanOrEqualTo(2);
        assertThat(response.matches()).isNotEmpty();
    }

    public void testSearchWithCaseSensitive() {
        myFixture.configureByText("CaseTest.java", """
                public class CaseTest {
                    String UPPER = "TEST";
                    String lower = "test";
                }
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        // Case-sensitive search for "TEST" should find only uppercase
        var result = tool.execute(Map.of(
                "searchText", "TEST",
                "projectPath", projectPath,
                "caseSensitive", true
        ));

        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);
        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.caseSensitive()).isTrue();
        // Should find "TEST" but not "test"
        assertThat(response.matches())
                .allMatch(match -> match.matchedText().equals("TEST"));
    }

    public void testSearchWithRegex() {
        myFixture.configureByText("RegexTest.java", """
                public class RegexTest {
                    int value1 = 100;
                    int value2 = 200;
                    int value3 = 300;
                }
                """);

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        // Regex search for "value\\d"
        var result = tool.execute(Map.of(
                "searchText", "value\\d",
                "projectPath", projectPath,
                "useRegex", true
        ));

        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);
        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.useRegex()).isTrue();
        assertThat(response.totalMatches()).isGreaterThanOrEqualTo(3);
        assertThat(response.matches())
                .extracting(SearchTextTool.SearchMatch::matchedText)
                .allMatch(text -> text.matches("value\\d"));
    }

    public void testSearchWithFilePattern() {
        myFixture.configureByText("SearchMe.java", "// target text in java");
        myFixture.configureByText("IgnoreMe.txt", "target text in txt");

        String projectPath = Objects.requireNonNull(getProject().getBasePath());

        // Search only in .java files
        var result = tool.execute(Map.of(
                "searchText", "target text",
                "projectPath", projectPath,
                "filePattern", "*.java"
        ));

        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);
        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.filePattern()).isEqualTo("*.java");
        assertThat(response.matches())
                .allMatch(match -> match.filePath().endsWith(".java"));
    }

    public void testSearchWithMaxResults() {
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

        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);
        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.totalMatches()).isEqualTo(10);
        assertThat(response.truncated()).isTrue();
        assertThat(response.matches()).hasSize(10);
    }

    public void testSearchMatchContainsLineInfo() {
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

        assertThat(result).isInstanceOf(McpTool.Result.SuccessResponse.class);
        var successResult = (McpTool.Result.SuccessResponse<ErrorResponse, SearchTextTool.SearchTextResponse>) result;
        var response = successResult.message();

        assertThat(response.matches()).hasSize(1);
        var match = response.matches().getFirst();

        assertThat(match.line()).isEqualTo(2);
        assertThat(match.column()).isGreaterThan(0);
        assertThat(match.matchedText()).isEqualTo("target");
        assertThat(match.lineContent()).contains("target");
        assertThat(match.filePath()).endsWith("LineInfo.java");
    }
}