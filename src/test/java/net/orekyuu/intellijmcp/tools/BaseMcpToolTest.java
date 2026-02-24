package net.orekyuu.intellijmcp.tools;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

abstract class BaseMcpToolTest<T extends McpTool<?>> {
    protected CodeInsightTestFixture myFixture;
    protected T tool;

    @BeforeEach
    void setUp() throws Exception {
        IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
        TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
                factory.createLightFixtureBuilder(null, "test");
        IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
        myFixture = factory.createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
        myFixture.setUp();

        tool = createTool();
    }

    @AfterEach
    void tearDown() throws Exception {
        myFixture.tearDown();
    }

    abstract T createTool();

    @Test
    void testDescription() {
      assertThat(tool.getDescription()).isNotBlank();
    }

    @Test
    void testSchema() {
      var schema = tool.getInputSchema();
      assertThat(schema).isNotNull();
      assertThat(schema.type()).isEqualTo("object");
    }

    protected Project getProject() {
        return myFixture.getProject();
    }
}
