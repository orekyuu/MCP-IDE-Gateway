package net.orekyuu.intellijmcp.comment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable data model representing an inline comment displayed in the editor.
 */
public final class InlineComment {

    private final String id;
    private final String filePath;
    private final int line;
    private final String comment;
    private final LocalDateTime createdAt;

    public InlineComment(String filePath, int line, String comment) {
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.line = line;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
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

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
