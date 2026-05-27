package com.loganalyzer.service;

import com.loganalyzer.model.LogEntry;
import com.loganalyzer.model.LogEntry.Level;
import com.loganalyzer.util.JsonUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw log lines into structured LogEntry objects.
 */
public class LogIngestionService {

    // Plain-text pattern: TIMESTAMP LEVEL [SERVICE] - MESSAGE
    private static final Pattern PLAIN_WITH_SERVICE = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s+([A-Z]+)\\s+([a-zA-Z0-9_-]+)\\s+-\\s+(.*)$"
    );

    private static final Pattern PLAIN_NO_SERVICE = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s+([A-Z]+)\\s+(.*)$"
    );

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    /**
     * Attempts to parse a single raw log line.
     * @param rawLine the original log line from a file or stream
     * @return a populated LogEntry, or null if parsing fails
     */
    public LogEntry parseLogLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return null;

        String trimmed = rawLine.strip();

        // Try JSON first (starts with '{')
        if (trimmed.startsWith("{")) {
            return parseJsonLog(trimmed);
        }

        return parsePlainLog(trimmed);
    }

    // --- Plain-text parsing ---

    private LogEntry parsePlainLog(String rawLine) {
        // Try pattern that expects an explicit "ServiceName - " component first
        Matcher m = PLAIN_WITH_SERVICE.matcher(rawLine);
        if (m.matches()) {
            LocalDateTime timestamp = parseTimestamp(m.group(1));
            Level level = parseLevel(m.group(2));
            String service = m.group(3).trim();
            String message = m.group(4).trim();
            return new LogEntry(timestamp, level, service, message, rawLine);
        }

        // Fall back to the pattern without a service component
        m = PLAIN_NO_SERVICE.matcher(rawLine);
        if (m.matches()) {
            LocalDateTime timestamp = parseTimestamp(m.group(1));
            Level level = parseLevel(m.group(2));
            String service = "unknown";
            String message = m.group(3).trim();
            return new LogEntry(timestamp, level, service, message, rawLine);
        }

        return null;
    }

    // --- JSON parsing ---

    private LogEntry parseJsonLog(String line) {
        try {
            Map<String, String> fields = JsonUtil.parseSimpleObject(line);

            String tsRaw = fields.getOrDefault("timestamp", fields.get("time"));
            String levelRaw = fields.getOrDefault("level", fields.getOrDefault("severity", "UNKNOWN"));
            String service = fields.getOrDefault("service", fields.getOrDefault("logger", "unknown"));
            String message = fields.getOrDefault("message", fields.get("msg"));

            LocalDateTime timestamp = tsRaw != null ? parseTimestamp(tsRaw) : LocalDateTime.now();
            Level level = parseLevel(levelRaw);

            return new LogEntry(timestamp, level, service, message, line);
        } catch (Exception e) {
            return null;
        }
    }

    // --- Helpers ---

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null) return LocalDateTime.now();
        String cleaned = raw.trim().replace('T', ' ');
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDateTime.now();
    }

    private Level parseLevel(String raw) {
        if (raw == null) return Level.UNKNOWN;
        switch (raw.trim().toUpperCase()) {
            case "TRACE":  return Level.TRACE;
            case "DEBUG":  return Level.DEBUG;
            case "INFO":   return Level.INFO;
            case "WARN":
            case "WARNING": return Level.WARN;
            case "ERROR":  return Level.ERROR;
            case "FATAL":
            case "CRITICAL": return Level.FATAL;
            default:       return Level.UNKNOWN;
        }
    }
}
