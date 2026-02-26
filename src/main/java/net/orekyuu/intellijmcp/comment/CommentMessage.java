package net.orekyuu.intellijmcp.comment;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single message in an inline comment thread.
 */
public final class CommentMessage {

    public enum Author { AI, USER }

    private final String messageId;
    private final Author author;
    private final String text;
    private final Instant createdAt;

    public CommentMessage(Author author, String text) {
        this.messageId = UUID.randomUUID().toString();
        this.author = author;
        this.text = text;
        this.createdAt = Instant.now();
    }

    CommentMessage(String messageId, Author author, String text, Instant createdAt) {
        this.messageId = messageId;
        this.author = author;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public Author getAuthor() {
        return author;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
