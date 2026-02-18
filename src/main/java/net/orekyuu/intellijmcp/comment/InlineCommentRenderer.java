package net.orekyuu.intellijmcp.comment;

import com.intellij.ide.BrowserUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Factory that creates Swing component panels for inline comments,
 * styled similarly to GitHub PR review comments.
 * <p>
 * Code block syntax highlighting follows the same approach as
 * intellij-community's GitHub plugin (CodeBlockHtmlSyntaxHighlighter):
 * uses IntelliJ's SyntaxHighlighterFactory + EditorColorsManager to
 * produce HTML with inline color styles.
 */
public final class InlineCommentRenderer {

    private static final Color COMMENT_BG = new JBColor(new Color(0xF6F8FA), new Color(0x2D333B));
    private static final Color HEADER_BG = new JBColor(new Color(0xDDF4FF), new Color(0x1C2128));
    private static final Color BORDER_COLOR = new JBColor(new Color(0xD0D7DE), new Color(0x444C56));
    private static final Color HEADER_FG = new JBColor(new Color(0x1F2328), new Color(0xADBBC8));

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            AutolinkExtension.create()
    );

    private InlineCommentRenderer() {}

    /**
     * Creates a comment panel styled like a GitHub PR review comment.
     */
    public static JComponent createCommentComponent(Editor editor, String commentText, Runnable onDismiss) {
        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme()
                .getFont(EditorFontType.PLAIN);
        int fontSize = editorFont.getSize();

        // Use the hard wrap (right margin) width in pixels,
        // minus card border and body padding so the outer edge aligns with the margin guide.
        int rightMarginColumns = editor.getSettings().getRightMargin(editor.getProject());
        int charWidth = editor.getContentComponent()
                .getFontMetrics(editorFont).charWidth('m');
        int cardBorderLR = 2;                   // createLineBorder 1px each side
        int bodyPaddingLR = JBUI.scale(8) * 2;  // bodyWrapper empty border left+right
        int contentWidth = rightMarginColumns * charWidth - cardBorderLR - bodyPaddingLR;

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(JBUI.Borders.empty(2, 0));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(COMMENT_BG);
        card.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(JBUI.Borders.empty(4, 8, 4, 4));

        JLabel titleLabel = new JLabel("AI Comment");
        titleLabel.setFont(editorFont.deriveFont(Font.BOLD, (float) fontSize));
        titleLabel.setForeground(HEADER_FG);
        header.add(titleLabel, BorderLayout.WEST);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(editorFont.deriveFont(Font.BOLD, (float) (fontSize + 2)));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setForeground(HEADER_FG);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(JBUI.scale(20), JBUI.scale(20)));
        closeBtn.addActionListener(e -> {
            if (onDismiss != null) onDismiss.run();
        });
        header.add(closeBtn, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);

        // Body
        Project project = editor.getProject();
        JEditorPane body = createBodyPane(commentText, project);
        JPanel bodyWrapper = new JPanel(new BorderLayout());
        bodyWrapper.setBackground(COMMENT_BG);
        bodyWrapper.setBorder(JBUI.Borders.empty(6, 8));
        bodyWrapper.add(body, BorderLayout.CENTER);

        card.add(bodyWrapper, BorderLayout.CENTER);

        outer.add(card, BorderLayout.CENTER);

        // Calculate proper preferred size so ComponentInlayRenderer gets a non-zero height.
        // JEditorPane needs the actual available width to compute wrapped text height correctly.
        int bodyAvailableWidth = contentWidth - cardBorderLR - bodyPaddingLR;
        body.setSize(new Dimension(Math.max(bodyAvailableWidth, 100), Integer.MAX_VALUE));
        Dimension bodyPref = body.getPreferredSize();

        // outer top/bottom padding(2+2) + card border top/bottom(1+1)
        // + header padding top/bottom(4+4) + bodyWrapper padding top/bottom(6+6)
        int verticalInsets = JBUI.scale(4) + cardBorderLR + JBUI.scale(8 + 12);
        int totalHeight = bodyPref.height + header.getPreferredSize().height + verticalInsets;
        outer.setPreferredSize(new Dimension(contentWidth, totalHeight));

        return outer;
    }

    private static JEditorPane createBodyPane(String commentText, Project project) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setOpaque(false);

        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                BrowserUtil.browse(e.getURL());
            }
        });

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = new StyleSheet();

        Color fg = JBColor.foreground();
        String fgHex = colorToHex(fg);
        String bgHex = colorToHex(COMMENT_BG);
        Color codeBg = new JBColor(new Color(0xEFF1F3), new Color(0x3B4048));
        String codeBgHex = colorToHex(codeBg);

        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme()
                .getFont(EditorFontType.PLAIN);
        String fontFamily = editorFont.getFamily();
        int fontSize = editorFont.getSize();
        int bodyFontSize = Math.max(fontSize - 1, 10);

        styleSheet.addRule("body { font-family: '" + fontFamily + "', sans-serif; font-size: " + bodyFontSize + "pt; color: " + fgHex + "; background-color: " + bgHex + "; margin: 0; padding: 0; }");
        styleSheet.addRule("code { font-family: '" + fontFamily + "', monospace; background-color: " + codeBgHex + "; padding: 1px 4px; }");
        styleSheet.addRule("pre { font-family: '" + fontFamily + "', monospace; font-size: " + (bodyFontSize - 1) + "pt; background-color: " + codeBgHex + "; padding: 8px; margin: 4px 0; white-space: pre-wrap; }");
        styleSheet.addRule("a { color: #0969da; text-decoration: underline; }");
        styleSheet.addRule("table { border-collapse: collapse; margin: 4px 0; }");
        styleSheet.addRule("th, td { border: 1px solid " + colorToHex(BORDER_COLOR) + "; padding: 3px 6px; }");
        styleSheet.addRule("th { background-color: " + codeBgHex + "; }");
        styleSheet.addRule("p { margin: 3px 0; }");
        styleSheet.addRule("ul, ol { margin: 3px 0; padding-left: 18px; }");
        styleSheet.addRule("blockquote { margin: 4px 0; padding-left: 8px; color: " + colorToHex(HEADER_FG) + "; }");
        styleSheet.addRule("h1 { font-size: " + (bodyFontSize + 3) + "pt; margin: 4px 0 3px 0; }");
        styleSheet.addRule("h2 { font-size: " + (bodyFontSize + 2) + "pt; margin: 4px 0 3px 0; }");
        styleSheet.addRule("h3 { font-size: " + (bodyFontSize + 1) + "pt; margin: 3px 0 2px 0; }");
        styleSheet.addRule("hr { border: 0; border-top: 1px solid " + colorToHex(BORDER_COLOR) + "; margin: 6px 0; }");

        kit.setStyleSheet(styleSheet);
        pane.setEditorKit(kit);

        String html = renderMarkdown(commentText, project);
        pane.setText(html);

        return pane;
    }

    private static String renderMarkdown(String markdown, Project project) {
        Parser parser = Parser.builder()
                .extensions(EXTENSIONS)
                .build();
        Node document = parser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .nodeRendererFactory(context -> new CodeBlockSyntaxHighlightingRenderer(context, project))
                .build();
        return renderer.render(document);
    }

    /**
     * Custom NodeRenderer that handles FencedCodeBlock and IndentedCodeBlock.
     * <p>
     * Equivalent to intellij-community's CodeFenceSyntaxHighlighterGeneratingProvider
     * + CodeBlockHtmlSyntaxHighlighter: detects the language from the code fence info string,
     * resolves an IntelliJ Language, then uses SyntaxHighlighterFactory to lex and colorize.
     */
    private static class CodeBlockSyntaxHighlightingRenderer implements NodeRenderer {
        private final HtmlNodeRendererContext context;
        private final Project project;

        CodeBlockSyntaxHighlightingRenderer(HtmlNodeRendererContext context, Project project) {
            this.context = context;
            this.project = project;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class, IndentedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof FencedCodeBlock fenced) {
                renderFenced(fenced);
            } else if (node instanceof IndentedCodeBlock indented) {
                renderIndented(indented);
            }
        }

        private void renderFenced(FencedCodeBlock codeBlock) {
            String info = codeBlock.getInfo();
            String languageId = (info != null && !info.isEmpty()) ? info.split("\\s+")[0] : null;
            String code = codeBlock.getLiteral();

            HtmlWriter html = context.getWriter();
            html.line();
            html.tag("pre");

            Map<String, String> codeAttrs = new LinkedHashMap<>();
            if (languageId != null) {
                codeAttrs.put("class", "language-" + languageId);
            }
            html.tag("code", codeAttrs);
            html.raw(colorize(languageId, code));
            html.tag("/code");
            html.tag("/pre");
            html.line();
        }

        private void renderIndented(IndentedCodeBlock codeBlock) {
            HtmlWriter html = context.getWriter();
            html.line();
            html.tag("pre");
            html.tag("code");
            html.raw(StringUtil.escapeXmlEntities(codeBlock.getLiteral()));
            html.tag("/code");
            html.tag("/pre");
            html.line();
        }

        /**
         * Colorizes source code using IntelliJ's SyntaxHighlighterFactory.
         * <p>
         * This mirrors the logic in {@code HtmlSyntaxHighlighter.colorHtmlChunk()} and
         * {@code CodeBlockHtmlSyntaxHighlighter.color()} from intellij-community's
         * platform/markdown-utils module.
         */
        private String colorize(String languageId, String rawCode) {
            Language language = findRegisteredLanguage(languageId);
            if (language == null) {
                return StringUtil.escapeXmlEntities(rawCode);
            }

            LightVirtualFile file = new LightVirtualFile("code_block_temp", rawCode);
            SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file);
            EditorColorsScheme colorScheme = EditorColorsManager.getInstance().getGlobalScheme();

            var lexer = highlighter.getHighlightingLexer();
            lexer.start(rawCode);

            StringBuilder result = new StringBuilder();
            while (lexer.getTokenType() != null) {
                String tokenText = StringUtil.escapeXmlEntities(lexer.getTokenText());
                var highlights = highlighter.getTokenHighlights(lexer.getTokenType());

                Color color = null;
                if (highlights.length > 0) {
                    var attrKey = highlights[highlights.length - 1];
                    TextAttributes attrs = colorScheme.getAttributes(attrKey);
                    if (attrs != null) {
                        color = attrs.getForegroundColor();
                    }
                    if (color == null) {
                        TextAttributes defaultAttrs = attrKey.getDefaultAttributes();
                        if (defaultAttrs != null) {
                            color = defaultAttrs.getForegroundColor();
                        }
                    }
                }

                if (color != null) {
                    result.append("<span style=\"color:")
                            .append(ColorUtil.toHtmlColor(color))
                            .append("\">")
                            .append(tokenText)
                            .append("</span>");
                } else {
                    result.append(tokenText);
                }

                lexer.advance();
            }

            return result.toString();
        }

        /**
         * Finds a registered IntelliJ Language by ID (case-insensitive).
         * Same approach as CodeBlockHtmlSyntaxHighlighter.findRegisteredLanguage().
         */
        private static Language findRegisteredLanguage(String languageId) {
            if (languageId == null || languageId.isEmpty()) {
                return null;
            }
            String lower = languageId.toLowerCase();
            return Language.getRegisteredLanguages().stream()
                    .filter(lang -> lang.getID().toLowerCase().equals(lower))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
