package net.orekyuu.intellijmcp.comment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Data model representing an inline comment thread displayed in the editor.
 */
public final class InlineComment {

    private final String id;
    private final String filePath;
    private final int line;
    private final CopyOnWriteArrayList<CommentMessage> messages;
    private boolean folded;

    public InlineComment(String filePath, int line, String comment) {
        this(filePath, line, comment, CommentMessage.Author.AI);
    }

    public InlineComment(String filePath, int line, String comment, CommentMessage.Author author) {
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.line = line;
        this.messages = new CopyOnWriteArrayList<>();
        this.messages.add(new CommentMessage(author, comment));
        this.folded = false;
    }

    InlineComment(String id, String filePath, int line, List<CommentMessage> messages) {
        this.id = id;
        this.filePath = filePath;
        this.line = line;
        this.messages = new CopyOnWriteArrayList<>(messages);
        this.folded = false;
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLine() {
        return line;
    }

    public List<CommentMessage> getMessages() {
        return List.copyOf(messages);
    }

    /** Returns the timestamp of the most recently added or edited message. */
    public Instant getUpdatedAt() {
        return messages.stream()
                .map(CommentMessage::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }

    /** Returns the text of the first message (for backward compatibility). */
    public String getFirstMessageText() {
        if (messages.isEmpty()) return "";
        return messages.get(0).getText();
    }

    public boolean isFolded() { return folded; }

    public void setFolded(boolean folded) { this.folded = folded; }

    void addMessage(CommentMessage message) {
        messages.add(message);
    }

    void updateMessage(String messageId, String newText) {
        for (int i = 0; i < messages.size(); i++) {
            CommentMessage m = messages.get(i);
            if (m.getMessageId().equals(messageId)) {
                messages.set(i, new CommentMessage(m.getMessageId(), m.getAuthor(), newText, m.getCreatedAt()));
                return;
            }
        }
    }

    boolean removeMessage(String messageId) {
        return messages.removeIf(m -> m.getMessageId().equals(messageId));
    }

    int getMessageCount() {
        return messages.size();
    }
}
