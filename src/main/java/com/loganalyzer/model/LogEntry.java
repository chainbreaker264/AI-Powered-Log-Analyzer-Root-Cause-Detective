package com.loganalyzer.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single parsed log entry from any log source.
 * Supports both plain-text and JSON log formats.
 */
public class LogEntry {

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN }

    private String id;
    private LocalDateTime timestamp;
    private Level level;
    private String service;
    private String message;
    private String rawLine;

    public LogEntry() {
        this.id = UUID.randomUUID().toString();
    }

    public LogEntry(LocalDateTime timestamp, Level level, String service, String message, String rawLine) {
        this();
        this.timestamp = timestamp;
        this.level = level;
        this.service = service;
        this.message = message;
        this.rawLine = rawLine;
    }

    // Getters
    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Level getLevel() { return level; }
    public String getService() { return service; }
    public String getMessage() { return message; }
    public String getRawLine() { return rawLine; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setLevel(Level level) { this.level = level; }
    public void setService(String service) { this.service = service; }
    public void setMessage(String message) { this.message = message; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    @Override
    public String toString() {
        return String.format("LogEntry{id='%s', timestamp=%s, level=%s, service='%s', message='%s'}",
                id, timestamp, level, service, message);
    }
}
