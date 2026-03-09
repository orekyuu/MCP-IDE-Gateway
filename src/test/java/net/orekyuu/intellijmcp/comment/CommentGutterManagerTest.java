package net.orekyuu.intellijmcp.comment;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.event.MouseEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CommentGutterManager#canDoAction}.
 *
 * <p>These tests are written test-first. The {@code columnHovered} guard in
 * {@code canDoAction} and the field itself do not yet exist in production code,
 * so the tests are expected to fail until the implementation is complete.
 */
public class CommentGutterManagerTest extends BasePlatformTestCase {

    private CommentGutterManager gutterManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.configureByText("Test.java", "public class Test {}\n");
        Editor editor = myFixture.getEditor();
        gutterManager = new CommentGutterManager(editor, getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        gutterManager.dispose();
        super.tearDown();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Y 座標がちょうどアイコン中央に当たる MouseEvent を返す。
     * アイコンサイズはテスト環境では JBUI.scale(16) == 16 と仮定する。
     */
    private MouseEvent mouseEventAtLine(Editor editor, int logicalLine) {
        int visualLine = editor.logicalToVisualPosition(new LogicalPosition(logicalLine, 0)).line;
        int lineY = editor.visualLineToY(visualLine);
        int iconSize = 16; // JBUI.scale(16) in headless test environment
        int iconY = lineY + (editor.getLineHeight() - iconSize) / 2;
        int clickY = iconY + iconSize / 2; // center of the icon
        return new MouseEvent(
                editor.getContentComponent(),
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                0, clickY,
                1, false
        );
    }

    /** 任意の Y 座標を持つ MouseEvent を返す。 */
    private MouseEvent mouseEventAtY(Editor editor, int y) {
        return new MouseEvent(
                editor.getContentComponent(),
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                0, y,
                1, false
        );
    }

    // ------------------------------------------------------------------
    // Test cases
    // ------------------------------------------------------------------

    /**
     * アイコン列上でクリックしたとき、canDoAction が true を返すこと。
     *
     * columnHovered=true かつ Y がアイコン行の範囲内であれば true を返す。
     */
    public void testCanDoActionReturnsTrueWhenColumnHoveredAndYIsInRange() {
        Editor editor = myFixture.getEditor();
        int hoverLine = 0;

        gutterManager.hoverLine = hoverLine;
        gutterManager.columnHovered = true;

        MouseEvent event = mouseEventAtLine(editor, hoverLine);
        assertThat(gutterManager.canDoAction(editor, event)).isTrue();
    }

    /**
     * アイコン列外（Y が正しくても）でクリックしたとき、canDoAction が false を返すこと。
     *
     * columnHovered=false の場合、Y 座標が正しくても false を返す。
     */
    public void testCanDoActionReturnsFalseWhenColumnNotHoveredEvenIfYIsInRange() {
        Editor editor = myFixture.getEditor();
        int hoverLine = 0;

        gutterManager.hoverLine = hoverLine;
        gutterManager.columnHovered = false;

        MouseEvent event = mouseEventAtLine(editor, hoverLine);
        assertThat(gutterManager.canDoAction(editor, event)).isFalse();
    }

    /**
     * Y 範囲外（X がアイコン列内でも）でクリックしたとき、canDoAction が false を返すこと。
     *
     * columnHovered=true でも、Y がアイコンの描画範囲外であれば false を返す。
     */
    public void testCanDoActionReturnsFalseWhenYIsOutOfIconRange() {
        Editor editor = myFixture.getEditor();
        int hoverLine = 0;

        gutterManager.hoverLine = hoverLine;
        gutterManager.columnHovered = true;

        // 画面外に相当する負の Y 座標はアイコン領域に決して重ならない
        MouseEvent event = mouseEventAtY(editor, -100);
        assertThat(gutterManager.canDoAction(editor, event)).isFalse();
    }

    /**
     * ホバーしていないとき（columnHovered=false）、canDoAction が false を返すこと。
     *
     * columnHovered フラグが false であれば、その他の条件に関わらず false を返す。
     */
    public void testCanDoActionReturnsFalseWhenNotHovering() {
        Editor editor = myFixture.getEditor();

        gutterManager.hoverLine = 0;
        gutterManager.columnHovered = false;

        MouseEvent event = mouseEventAtLine(editor, 0);
        assertThat(gutterManager.canDoAction(editor, event)).isFalse();
    }
}
