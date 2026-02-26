package net.orekyuu.intellijmcp.comment;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.*;
import java.util.List;

/**
 * Factory that creates Swing component panels for inline comment threads,
 * styled similarly to GitHub PR review comments.
 */
public final class InlineCommentRenderer {

    private static final Color COMMENT_BG = new JBColor(new Color(0xF6F8FA), new Color(0x2D333B));
    private static final Color HEADER_BG = new JBColor(new Color(0xDDF4FF), new Color(0x1C2128));
    private static final Color BORDER_COLOR = new JBColor(new Color(0xD0D7DE), new Color(0x444C56));
    private static final Color HEADER_FG = new JBColor(new Color(0x1F2328), new Color(0xADBBC8));
    private static final Color AI_BADGE_BG = new JBColor(new Color(0xDDF4FF), new Color(0x1C2B3C));
    private static final Color AI_BADGE_FG = new JBColor(new Color(0x0969DA), new Color(0x79C0FF));
    private static final Color USER_BADGE_BG = new JBColor(new Color(0xFFF8C5), new Color(0x3B2D00));
    private static final Color USER_BADGE_FG = new JBColor(new Color(0x7D4E00), new Color(0xF0C000));

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
    );

    private InlineCommentRenderer() {}

    /**
     * Creates a thread comment panel styled like a GitHub PR review comment.
     *
     * @param onFoldToggle called when the user clicks fold/unfold to trigger inlay refresh
     */
    public static JComponent createCommentComponent(
            Editor editor,
            InlineComment comment,
            Runnable onDismiss,
            Runnable onFoldToggle
    ) {
        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme()
                .getFont(EditorFontType.PLAIN);
        int fontSize = editorFont.getSize();

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

        // Messages panel
        Project project = editor.getProject();
        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(COMMENT_BG);
        messagesPanel.setBorder(JBUI.Borders.empty(6, 8, 4, 8));

        List<CommentMessage> messages = comment.getMessages();
        boolean canFold = messages.size() > 3;
        boolean folded = comment.isFolded() && canFold;

        if (!folded) {
            // Show all messages
            for (int i = 0; i < messages.size(); i++) {
                CommentMessage msg = messages.get(i);
                if (i > 0) {
                    JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
                    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(1)));
                    messagesPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
                    messagesPanel.add(sep);
                    messagesPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
                }
                JPanel msgPanel = createMessagePanel(msg, editorFont, fontSize, project, comment);
                msgPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                messagesPanel.add(msgPanel);
            }
        } else {
            // Show first message
            JPanel firstPanel = createMessagePanel(messages.get(0), editorFont, fontSize, project, comment);
            firstPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagesPanel.add(firstPanel);

            // "N more replies" button
            int hiddenCount = messages.size() - 2;
            JButton unfoldBtn = new JButton(hiddenCount + " more " + (hiddenCount == 1 ? "reply" : "replies"));
            unfoldBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            unfoldBtn.addActionListener(e -> {
                comment.setFolded(false);
                if (onFoldToggle != null) onFoldToggle.run();
            });
            messagesPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
            messagesPanel.add(unfoldBtn);

            // Show last message
            JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(1)));
            messagesPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
            messagesPanel.add(sep);
            messagesPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
            JPanel lastPanel = createMessagePanel(messages.get(messages.size() - 1), editorFont, fontSize, project, comment);
            lastPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagesPanel.add(lastPanel);
        }

        card.add(messagesPanel, BorderLayout.CENTER);

        // Reply panel
        JPanel replyPanel = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        replyPanel.setBackground(COMMENT_BG);
        replyPanel.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        if (project != null) {
            // EditorTextField for reply input (Ctrl+Enter to send)
            EditorTextField replyField = new EditorTextField("", project, PlainTextFileType.INSTANCE);
            replyField.setOneLineMode(false);
            replyField.setFontInheritedFromLAF(true);
            replyField.setToolTipText("Reply... (Ctrl+Enter to send)");

            JButton replyBtn = new JButton("Reply");
            replyBtn.setFont(editorFont.deriveFont(Font.BOLD, (float) Math.max(fontSize - 1, 10)));
            replyBtn.setAlignmentY(Component.TOP_ALIGNMENT);

            replyPanel.add(replyField, BorderLayout.CENTER);
            replyPanel.add(replyBtn, BorderLayout.EAST);

            Runnable sendReply = () -> {
                String text = replyField.getText().trim();
                if (text.isEmpty()) return;
                replyField.setText("");
                ApplicationManager.getApplication().executeOnPooledThread(() ->
                        InlineCommentService.getInstance(project)
                                .addReply(comment.getId(), CommentMessage.Author.USER, text)
                );
            };
            replyBtn.addActionListener(e -> sendReply.run());

            // Register Ctrl+Enter shortcut via addSettingsProvider
            Disposable keyDisposable = Disposer.newDisposable("reply-shortcut-handler");
            replyField.addSettingsProvider(innerEditor -> {
                DumbAwareAction submitAction = new DumbAwareAction() {
                    @Override
                    public @NotNull ActionUpdateThread getActionUpdateThread() {
                        return ActionUpdateThread.EDT;
                    }

                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        SwingUtilities.invokeLater(sendReply);
                    }
                };
                submitAction.registerCustomShortcutSet(
                        CommonShortcuts.getCtrlEnter(),
                        innerEditor.getContentComponent(),
                        keyDisposable
                );
                innerEditor.getSettings().setUseSoftWraps(true);
            });

            replyField.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                        && !replyField.isDisplayable()
                        && !Disposer.isDisposed(keyDisposable)) {
                    Disposer.dispose(keyDisposable);
                }
            });

            // Fold button (visible when thread has > 3 messages and is currently unfolded)
            if (canFold && !folded) {
                JButton foldBtn = new JButton("Collapse");
                foldBtn.addActionListener(e -> {
                    comment.setFolded(true);
                    if (onFoldToggle != null) onFoldToggle.run();
                });
                replyPanel.add(foldBtn, BorderLayout.WEST);
            }
        }

        card.add(replyPanel, BorderLayout.SOUTH);

        outer.add(card, BorderLayout.CENTER);

        return outer;
    }

    private static JPanel createMessagePanel(CommentMessage msg, Font editorFont, int fontSize, Project project, InlineComment comment) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COMMENT_BG);

        // Author badge
        boolean isAI = msg.getAuthor() == CommentMessage.Author.AI;
        String badgeText = isAI ? "AI" : "You";
        Color badgeBg = isAI ? AI_BADGE_BG : USER_BADGE_BG;
        Color badgeFg = isAI ? AI_BADGE_FG : USER_BADGE_FG;

        JLabel badge = new JLabel(badgeText) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(badgeBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(6), JBUI.scale(6));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(editorFont.deriveFont(Font.BOLD, (float) Math.max(fontSize - 2, 9)));
        badge.setForeground(badgeFg);
        badge.setOpaque(false);
        badge.setBorder(JBUI.Borders.empty(1, 4, 1, 4));

        JPanel badgeWrapper = new JPanel(new BorderLayout());
        badgeWrapper.setBackground(COMMENT_BG);
        badgeWrapper.setBorder(JBUI.Borders.emptyBottom(2));

        JPanel badgeLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeLeft.setBackground(COMMENT_BG);
        badgeLeft.add(badge);
        badgeWrapper.add(badgeLeft, BorderLayout.WEST);

        // CardLayout to switch between view and edit modes
        JPanel contentCard = new JPanel(new CardLayout());
        contentCard.setBackground(COMMENT_BG);

        JEditorPane body = createBodyPane(msg.getText(), project);
        contentCard.add(body, "view");

        if (!isAI && project != null) {
            // Icon buttons (edit + delete) in header
            JButton editBtn = new JButton(AllIcons.Actions.Edit);
            editBtn.setBorderPainted(false);
            editBtn.setContentAreaFilled(false);
            editBtn.setFocusPainted(false);
            editBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            editBtn.setToolTipText("Edit");
            editBtn.setPreferredSize(new Dimension(JBUI.scale(20), JBUI.scale(20)));

            JButton deleteBtn = new JButton(AllIcons.Actions.GC);
            deleteBtn.setBorderPainted(false);
            deleteBtn.setContentAreaFilled(false);
            deleteBtn.setFocusPainted(false);
            deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            deleteBtn.setToolTipText("Delete");
            deleteBtn.setPreferredSize(new Dimension(JBUI.scale(20), JBUI.scale(20)));

            JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actionButtons.setBackground(COMMENT_BG);
            actionButtons.add(editBtn);
            actionButtons.add(deleteBtn);
            badgeWrapper.add(actionButtons, BorderLayout.EAST);

            // Edit mode UI
            EditorTextField editField = new EditorTextField(msg.getText(), project, PlainTextFileType.INSTANCE);
            editField.setOneLineMode(false);

            JButton saveBtn = new JButton("Save");
            JButton cancelBtn = new JButton("Cancel");
            saveBtn.setFont(editorFont.deriveFont((float) Math.max(fontSize - 1, 10)));
            cancelBtn.setFont(editorFont.deriveFont((float) Math.max(fontSize - 1, 10)));

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0));
            btnPanel.setBackground(COMMENT_BG);
            btnPanel.add(cancelBtn);
            btnPanel.add(saveBtn);

            JPanel editContainer = new JPanel(new BorderLayout(0, JBUI.scale(4)));
            editContainer.setBackground(COMMENT_BG);
            editContainer.add(editField, BorderLayout.CENTER);
            editContainer.add(btnPanel, BorderLayout.SOUTH);
            contentCard.add(editContainer, "edit");

            editBtn.addActionListener(e -> {
                editField.setText(msg.getText());
                ((CardLayout) contentCard.getLayout()).show(contentCard, "edit");
            });
            cancelBtn.addActionListener(e ->
                    ((CardLayout) contentCard.getLayout()).show(contentCard, "view"));
            saveBtn.addActionListener(e -> {
                String newText = editField.getText().trim();
                if (!newText.isEmpty()) {
                    ApplicationManager.getApplication().executeOnPooledThread(() ->
                            InlineCommentService.getInstance(project)
                                    .updateMessage(comment.getId(), msg.getMessageId(), newText)
                    );
                }
            });
            deleteBtn.addActionListener(e ->
                    ApplicationManager.getApplication().executeOnPooledThread(() ->
                            InlineCommentService.getInstance(project)
                                    .removeMessage(comment.getId(), msg.getMessageId())
                    )
            );
        }

        panel.add(badgeWrapper, BorderLayout.NORTH);
        panel.add(contentCard, BorderLayout.CENTER);

        return panel;
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
        styleSheet.addRule("del { text-decoration: line-through; }");

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
            return Set.of(FencedCodeBlock.class, IndentedCodeBlock.class, SoftLineBreak.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof FencedCodeBlock fenced) {
                renderFenced(fenced);
            } else if (node instanceof IndentedCodeBlock indented) {
                renderIndented(indented);
            } else if (node instanceof SoftLineBreak) {
                context.getWriter().raw("<br>\n");
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
