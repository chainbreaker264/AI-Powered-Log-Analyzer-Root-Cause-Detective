package com.loganalyzer.service;

import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.util.ApiClient;
import com.loganalyzer.util.JsonUtil;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sends clustered log entries to the Anthropic Claude API and parses the
 * structured root-cause analysis response.
 */
public class RootCauseAnalysisService {

    private static final Logger LOG = Logger.getLogger(RootCauseAnalysisService.class.getName());
    
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
    private static final int MAX_TOKENS = 1024;
    private static final int MAX_LOG_LINES_PER_CLUSTER = 20;

    private final String apiKey;

    /**
     * @param apiKey Anthropic API key. Use "OFFLINE" for heuristic-only mode.
     */
    public RootCauseAnalysisService(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A valid Anthropic API key must be provided. " +
                "Set anthropic.api-key in application.properties or pass --no-ai for offline mode.");
        }
        this.apiKey = apiKey;
    }

    // --- Public API ---

    /**
     * Analyses a cluster of similar log entries using Claude and returns a 
     * structured AnalysisResult.
     * Falls back to heuristic analysis when API key is "OFFLINE".
     * @param clusterEntries the log entries belonging to one error cluster
     * @return result analysis result (never null)
     */
    public AnalysisResult analyzeCluster(List<LogEntry> clusterEntries) {
        if (clusterEntries == null || clusterEntries.isEmpty()) {
            return fallbackResult("empty-cluster", "No entries to analyze.", "", "", 0.0);
        }

        String representativeId = clusterEntries.get(0).getId();

        // Skip network call when running in offline/heuristic mode
        if (apiKey.equalsIgnoreCase("OFFLINE")) {
            return runHeuristicAnalysis(clusterEntries);
        }

        try {
            String prompt = buildPrompt(clusterEntries);
            String responseJson = ApiClient.postToAnthropic(prompt, apiKey, DEFAULT_MODEL, MAX_TOKENS);
            String replyText = JsonUtil.extractAssistantReply(responseJson);
            return parseReply(replyText, representativeId);
        } catch (Exception e) {
            LOG.warning("Anthropic API call failed; using heuristic fallback. Reason: " + e.getMessage());
            return runHeuristicAnalysis(clusterEntries);
        }
    }

    // --- Prompt construction ---

    private String buildPrompt(List<LogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert Site Reliability Engineer. Analyze the following cluster of ");
        sb.append("similar log entries and identify the root cause.\n\n");
        sb.append("### LOG ENTRIES:\n");

        int limit = Math.min(entries.size(), MAX_LOG_LINES_PER_CLUSTER);
        for (int i = 0; i < limit; i++) {
            LogEntry e = entries.get(i);
            sb.append(String.format("[%d] %s\n", (i + 1), e.getRawLine()));
        }

        if (entries.size() > MAX_LOG_LINES_PER_CLUSTER) {
            sb.append(String.format("... and %d more similar entries.\n", entries.size() - MAX_LOG_LINES_PER_CLUSTER));
        }

        sb.append("\nRespond ONLY with a valid JSON object in this exact format (no markdown, no explanation):\n");
        sb.append("{\n");
        sb.append("  \"errorPattern\": \"Short label for the recurring error pattern\",\n");
        sb.append("  \"rootCause\": \"Human-readable concise explanation of what caused the error\",\n");
        sb.append("  \"fixSuggestion\": \"Actionable remediation step\",\n");
        sb.append("  \"confidenceScore\": <number between 0.0 and 1.0>\n");
        sb.append("}\n");

        return sb.toString();
    }

    // --- Response parsing ---

    private AnalysisResult parseReply(String replyText, String representativeId) {
        if (replyText == null || replyText.isBlank()) {
            return fallbackResult(representativeId, "Empty reply", "Unable to parse response.", "", 0.0);
        }

        // Strip any markdown code fences
        String cleaned = replyText
            .replaceAll("```json", "")
            .replaceAll("```", "")
            .trim();

        // Extract the first JSON object
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");
        if (start == -1 || end == -1 || end < start) {
            return fallbackResult(representativeId, "Malformed LLM response", replyText, "", 0.0);
        }

        String jsonBlock = cleaned.substring(start, end + 1);

        try {
            java.util.Map<String, String> map = JsonUtil.parseSimpleObject(jsonBlock);
            String errorPattern = map.getOrDefault("errorPattern", "Unknown pattern");
            String rootCause = map.getOrDefault("rootCause", "Could not determine root cause");
            String fixSuggestion = map.getOrDefault("fixSuggestion", "Review logs manually");
            double confidence = 1.0;
            try {
                confidence = Double.parseDouble(map.getOrDefault("confidenceScore", "1.0"));
            } catch (Exception ignored) {}

            return new AnalysisResult(representativeId, errorPattern, rootCause, fixSuggestion, 
                Math.max(0.0, Math.min(1.0, confidence)));
        } catch (Exception e) {
            return fallbackResult(representativeId, "Parsing failure", jsonBlock, "", 0.5);
        }
    }

    // --- Heuristic Fallback ---

    /**
     * Simple keyword-based analysis used when API is unavailable.
     */
    private AnalysisResult runHeuristicAnalysis(List<LogEntry> entries) {
        LogEntry first = entries.get(0);
        String msg = first.getMessage() != null ? first.getMessage().toLowerCase() : "";
        String id = first.getId();

        if (msg.contains("connection") || msg.contains("timeout") || msg.contains("refused")) {
            return fallbackResult(id, "Network/Connection Error",
                "Connection refused, timeout, or network error.",
                "Check network connectivity, firewall rules, and downstream service health.", 0.7);
        }
        if (msg.contains("nullpointer") || msg.contains("null pointer") || msg.contains("npe")) {
            return fallbackResult(id, "NullPointerException",
                "An object reference was used before being initialized.",
                "Add null-checks or use Optional. Review the stack trace for the offending field.", 0.7);
        }
        if (msg.contains("out of memory") || msg.contains("outofmemory") || msg.contains("heap")) {
            return fallbackResult(id, "Out-of-Memory Error",
                "JVM heap exhausted - possible memory leak or under-provisioned heap.",
                "Increase max heap size, profile memory usage with VisualVM, and review object lifecycle.", 0.7);
        }
        if (msg.contains("authentication") || msg.contains("unauthorized") || msg.contains("forbidden")) {
            return fallbackResult(id, "Authentication/Authorization Failure",
                "Request rejected due to missing or invalid credentials.",
                "Verify API keys, token expiry, and RBAC policies.", 0.6);
        }

        return fallbackResult(id, "Unclassified Error",
            "Pattern not recognized by heuristic analysis. Enable Anthropic API for full analysis.",
            "Review the full stack trace and correlated logs for surrounding context.", 0.3);
    }

    // --- Helpers ---

    private AnalysisResult fallbackResult(String id, String pattern, String cause, String fix, double confidence) {
        return new AnalysisResult(id, "[Heuristic] " + pattern, cause, fix, confidence);
    }
}
