package net.orekyuu.intellijmcp.comment;

import java.util.UUID;

/**
 * Immutable data model representing an inline comment displayed in the editor.
 */
public final class InlineComment {

    private final String id;
    private final String filePath;
    private final int line;
    private final String comment;

    public InlineComment(String filePath, int line, String comment) {
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.line = line;
        this.comment = comment;
    }

    InlineComment(String id, String filePath, int line, String comment) {
        this.id = id;
        this.filePath = filePath;
        this.line = line;
        this.comment = comment;
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
}
