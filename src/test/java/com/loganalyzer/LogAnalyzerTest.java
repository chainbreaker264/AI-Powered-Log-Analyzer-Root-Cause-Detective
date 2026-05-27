package com.loganalyzer;

import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.service.EmbeddingService;
import com.loganalyzer.service.LogIngestionService;
import com.loganalyzer.util.JsonUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Lightweight unit tests - runnable without JUnit via a plain main() method.
 * Compile with javac, Run with java -cp output command
 */
public class LogAnalyzerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running Log Analyzer Unit Tests ===");

        try {
            testLogIngestionPlainLogText();
            testLogIngestionJsonFormat();
            testLogIngestionInvalidLine();
            testEmbeddingSameMessageSimilarity();
            testEmbeddingDifferentMessageLowSimilarity();
            testJsonUtilExtractString();
            testJsonUtilExtractNumber();
            testJsonUtilBuildAnthropicRequest();
            testJsonUtilExtractAssistantReply();
            testClusteringSimilarLogs();

            System.out.println();
            System.out.printf("Test Results: %d passed, %d failed.\n", passed, failed);
            if (failed > 0) System.exit(1);
        } catch (Exception e) {
            System.err.println("Test runner crashed!");
            e.printStackTrace();
            System.exit(2);
        }
    }

    // --- LogIngestionService tests ---

    static void testLogIngestionPlainLogText() {
        LogIngestionService svc = new LogIngestionService();
        String line = "2024-01-15 10:23:45 ERROR OrderService - Connection refused to db:3306";
        LogEntry entry = svc.parseLogLine(line);

        assertTrue("Plain log parsed (not null)", entry != null);
        if (entry != null) {
            assertTrue("Level is ERROR", entry.getLevel() == LogEntry.Level.ERROR);
            assertTrue("Service extracted", "OrderService".equals(entry.getService()));
            assertTrue("Message extracted", "Connection refused to db:3306".equals(entry.getMessage()));
        }
    }

    static void testLogIngestionJsonFormat() {
        LogIngestionService svc = new LogIngestionService();
        String line = "{\"timestamp\":\"2024-01-15 10:23:45\",\"level\":\"WARN\",\"service\":\"PaymentService\",\"message\":\"Slow response 3200ms\"}";
        LogEntry entry = svc.parseLogLine(line);

        assertTrue("JSON log parsed (not null)", entry != null);
        if (entry != null) {
            assertTrue("Level is WARN", entry.getLevel() == LogEntry.Level.WARN);
            assertTrue("Service extracted", "PaymentService".equals(entry.getService()));
            assertTrue("Message extracted", "Slow response 3200ms".equals(entry.getMessage()));
        }
    }

    static void testLogIngestionInvalidLine() {
        LogIngestionService svc = new LogIngestionService();
        String line = "this is not a valid log line at all @#$!!";
        LogEntry entry = svc.parseLogLine(line);
        assertTrue("Invalid line returns null", entry == null);
    }

    // --- EmbeddingService tests ---

    static void testEmbeddingSameMessageSimilarity() {
        EmbeddingService svc = new EmbeddingService();
        Map<String, float[]> embeddings = svc.embedLogEntries(Arrays.asList(
            new LogEntry(null, null, "svc", "2024-01-15 10:23:45 ERROR Svc - Connection refused to database", ""),
            new LogEntry(null, null, "svc", "2024-01-15 10:23:46 ERROR Svc - Connection refused to database", "")
        ));

        float[] v1 = embeddings.values().toArray(new float[0][])[0];
        float[] v2 = embeddings.values().toArray(new float[0][])[1];

        double sim = svc.cosineSimilarity(v1, v2);
        assertTrue("Identical messages have high similarity", sim >= 0.99);
    }

    static void testEmbeddingDifferentMessageLowSimilarity() {
        EmbeddingService svc = new EmbeddingService();
        Map<String, float[]> embeddings = svc.embedLogEntries(Arrays.asList(
            new LogEntry(null, null, "svc", "2024-01-15 10:23:45 ERROR Svc - Connection refused to database", ""),
            new LogEntry(null, null, "svc", "2024-01-15 10:25:04 INFO Svc - Application started successfully", "")
        ));

        float[] v1 = embeddings.values().toArray(new float[0][])[0];
        float[] v2 = embeddings.values().toArray(new float[0][])[1];

        double sim = svc.cosineSimilarity(v1, v2);
        assertTrue("Different messages have low similarity", sim < 0.85);
    }

    // --- JsonUtil tests ---

    static void testJsonUtilExtractString() {
        String json = "{\"errorPattern\":\"NullPointerException\",\"rootCause\":\"Uninitialized field\"}";
        String val = JsonUtil.extractString(json, "errorPattern");
        assertTrue("extractString works", "NullPointerException".equals(val));
    }

    static void testJsonUtilExtractNumber() {
        String json = "{\"confidenceScore\": 0.92, \"verified\": true}";
        String val = JsonUtil.extractNumber(json, "confidenceScore");
        assertTrue("extractNumber works", val != null && val.contains("0.92"));
    }

    static void testJsonUtilBuildAnthropicRequest() {
        String json = JsonUtil.buildAnthropicRequest("claude-3-5-sonnet-20241022", 512, "Hello");
        assertTrue("Contains model", json.contains("\"model\":\"claude-3-5-sonnet-20241022\""));
        assertTrue("Contains max_tokens", json.contains("\"max_tokens\":512"));
        assertTrue("Contains content", json.contains("Hello"));
    }

    static void testJsonUtilExtractAssistantReply() {
        String response = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello from Claude\"}],\"role\":\"assistant\"}";
        String reply = JsonUtil.extractAssistantReply(response);
        assertTrue("extractAssistantReply works", "Hello from Claude".equals(reply));
    }

    // --- Clustering test ---

    static void testClusteringSimilarLogs() {
        EmbeddingService svc = new EmbeddingService();
        LogIngestionService ingestionSvc = new LogIngestionService();

        List<LogEntry> entries = Arrays.asList(
            ingestionSvc.parseLogLine("2024-01-15 10:23:45 ERROR Svc - Connection refused to database"),
            ingestionSvc.parseLogLine("2024-01-15 10:23:46 ERROR Svc - Connection refused to database"),
            ingestionSvc.parseLogLine("2024-01-15 10:25:04 INFO Svc - Application started successfully")
        );

        Map<String, float[]> embeddings = svc.embedLogEntries(entries);
        Map<String, List<String>> clusters = svc.clusterSimilarErrors(embeddings);

        assertTrue("At least 2 clusters formed", clusters.size() >= 2);

        boolean hasTwoMemberCluster = clusters.values().stream().anyMatch(list -> list.size() == 2);
        assertTrue("Two similar errors grouped into one cluster", hasTwoMemberCluster);
    }

    // --- Test runner helpers ---

    static void assertTrue(String label, boolean condition) {
        if (condition) {
            System.out.println("  [ PASS ] " + label);
            passed++;
        } else {
            System.out.println("  [ FAIL ] " + label);
            failed++;
        }
    }
}
