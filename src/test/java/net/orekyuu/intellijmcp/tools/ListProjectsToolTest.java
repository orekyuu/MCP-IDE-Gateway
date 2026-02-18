package net.orekyuu.intellijmcp.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ListProjectsToolTest extends BaseMcpToolTest<ListProjectsTool> {

    @Override
    ListProjectsTool createTool() {
        return new ListProjectsTool();
    }

    @Test
    void executeReturnsProjects() {
        var result = tool.execute(Map.of());

        var response = McpToolResultAssert.assertThat(result).getSuccessResponse();

        assertThat(response.projects()).isNotNull();
        assertThat(response.projects().size()).isGreaterThanOrEqualTo(1);

        ListProjectsTool.ListProjectsResponse.ProjectInfo project = response.projects().getFirst();
        assertThat(project.name()).isNotNull();
        assertThat(project.basePath()).isNotNull();
    }

}
