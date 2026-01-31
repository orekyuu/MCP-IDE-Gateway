package net.orekyuu.intellijmcp.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.Topic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service that manages MCP server logs and notifies listeners.
 */
@Service
public final class McpServerLogService {

    public static final Topic<LogListener> LOG_TOPIC =
            Topic.create("MCP Server Log", LogListener.class);

    private static final int MAX_LOG_ENTRIES = 10000;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final List<LogEntry> logEntries = new CopyOnWriteArrayList<>();

    public static McpServerLogService getInstance() {
        return ApplicationManager.getApplication().getService(McpServerLogService.class);
    }

    public void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(LocalDateTime.now(), level, message);
        logEntries.add(entry);

        // Trim old entries if necessary
        while (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.removeFirst();
        }

        // Notify listeners
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(LOG_TOPIC)
                .onLogEntry(entry);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public List<LogEntry> getLogEntries() {
        return new ArrayList<>(logEntries);
    }

    public void clear() {
        logEntries.clear();
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(LOG_TOPIC)
                .onLogCleared();
    }

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public record LogEntry(LocalDateTime timestamp, LogLevel level, String message) {
        public String format() {
            return String.format("[%s] [%s] %s",
                    TIME_FORMATTER.format(timestamp),
                    level.name(),
                    message);
        }
    }

    public interface LogListener {
        void onLogEntry(LogEntry entry);
        default void onLogCleared() {}
    }
}
