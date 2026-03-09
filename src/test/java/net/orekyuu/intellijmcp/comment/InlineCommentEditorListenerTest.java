package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InlineCommentEditorListener#isIrrelevantEditor}.
 *
 * <p>{@code isIrrelevantEditor} is package-private to allow direct access from tests
 * in the same package without reflection.
 *
 * <p>The Viewer editor test case is written test-first: the {@code ex.isViewer()} guard
 * does not yet exist in production code, so that test is expected to fail until the
 * implementation is complete.
 */
public class InlineCommentEditorListenerTest extends BasePlatformTestCase {

    private InlineCommentEditorListener listener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Instantiate directly: the class is a @Service but is not registered in plugin.xml,
        // so project.getService() is unavailable in the test environment.
        listener = new InlineCommentEditorListener(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        listener.dispose();
        super.tearDown();
    }

    // ------------------------------------------------------------------
    // Test cases
    // ------------------------------------------------------------------

    /**
     * Viewer エディタのとき、isIrrelevantEditor が true を返すこと。
     *
     * InlineCommentsPanel のファイルプレビューなど isViewer()==true の EditorEx は
     * ガターマネージャーの管理対象外とするため true を返す必要がある。
     * 仕様書の実装変更後にこのアサーションが通ることを確認する。
     */
    public void testIsIrrelevantEditorReturnsTrueForViewerEditor() {
        myFixture.configureByText("ViewerTest.java", "public class ViewerTest {}\n");

        EditorFactory factory = EditorFactory.getInstance();
        Editor viewerEditor = factory.createViewer(myFixture.getEditor().getDocument(), getProject());
        try {
            assertTrue("createViewer should return EditorEx", viewerEditor instanceof EditorEx);
            assertTrue("viewer editor must report isViewer()=true", ((EditorEx) viewerEditor).isViewer());

            assertThat(listener.isIrrelevantEditor(viewerEditor)).isTrue();
        } finally {
            factory.releaseEditor(viewerEditor);
        }
    }

    /**
     * 通常エディタのとき、isIrrelevantEditor が false を返すこと。
     *
     * 同じプロジェクトに属する通常の Editor は管理対象であるため false を返す。
     */
    public void testIsIrrelevantEditorReturnsFalseForNormalEditor() {
        myFixture.configureByText("NormalTest.java", "public class NormalTest {}\n");
        Editor editor = myFixture.getEditor();

        assertThat(listener.isIrrelevantEditor(editor)).isFalse();
    }
}
