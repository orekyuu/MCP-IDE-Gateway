package net.orekyuu.intellijmcp.tools;

/**
 * Represents a range of lines in a file.
 * Both startLine and endLine are 1-indexed.
 */
public record LineRange(
        int startLine,
        int endLine,
        int lineCount
) {
    public LineRange(int startLine, int endLine) {
        this(startLine, endLine, endLine - startLine + 1);
    }
}
