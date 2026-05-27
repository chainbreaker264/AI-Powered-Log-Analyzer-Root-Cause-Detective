package com.loganalyzer;

import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.service.LogIngestionService;
import com.loganalyzer.service.EmbeddingService;
import com.loganalyzer.service.RootCauseAnalysisService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates the full log-analysis pipeline.
 */
public class LogAnalysisOrchestrator {

    private static final Logger LOG = Logger.getLogger(LogAnalysisOrchestrator.class.getName());
    private static final int SMALL_CLUSTER_WARN_THRESHOLD = 2;

    private final LogIngestionService ingestionService;
    private final EmbeddingService embeddingService;
    private final RootCauseAnalysisService rcaService;

    /**
     * Production constructor (String anthropicApiKey).
     */
    public LogAnalysisOrchestrator(String anthropicApiKey) {
        this.ingestionService = new LogIngestionService();
        this.embeddingService = new EmbeddingService();
        
        String effectiveKey = (anthropicApiKey == null || anthropicApiKey.isBlank()) ? "OFFLINE" : anthropicApiKey.trim();
        if (effectiveKey.equalsIgnoreCase("OFFLINE")) {
            this.rcaService = new RootCauseAnalysisService("OFFLINE");
        } else {
            this.rcaService = new RootCauseAnalysisService(effectiveKey);
        }
    }

    /**
     * Constructor for dependency injection (useful in tests).
     */
    public LogAnalysisOrchestrator(LogIngestionService ingestionService, 
                                   EmbeddingService embeddingService, 
                                   RootCauseAnalysisService rcaService) {
        this.ingestionService = ingestionService;
        this.embeddingService = embeddingService;
        this.rcaService = rcaService;
    }

    // --- Main pipeline ---

    /**
     * Processes a batch of raw log lines end-to-end.
     * @param rawLogLines list of raw log strings (may include blank lines)
     * @return one AnalysisResult per detected cluster (never null, may be empty)
     */
    public List<AnalysisResult> analyzeLogBatch(List<String> rawLogLines) {
        LOG.info("=== Log Analysis Pipeline Started ===");
        LOG.info("Stage 1: Ingesting logs...");

        // --- Stage 1: Ingest ---
        List<LogEntry> entries = new ArrayList<>();
        for (String line : rawLogLines) {
            LogEntry entry = ingestionService.parseLogLine(line);
            if (entry != null) entries.add(entry);
        }
        LOG.info(String.format("Stage 1 Ingest complete. (%d/%d entries parsed)", entries.size(), rawLogLines.size()));

        if (entries.isEmpty()) {
            LOG.warning("No parseable log entries found - check the input format.");
            return new ArrayList<>();
        }

        // --- Stage 2: Embed ---
        LOG.info("Stage 2: Embedding...");
        Map<String, float[]> embeddings = embeddingService.embedLogEntries(entries);
        LOG.info(String.format("Stage 2 complete (%d vectors generated).", embeddings.size()));

        // --- Stage 3: Cluster ---
        LOG.info("Stage 3: Clustering...");
        Map<String, List<String>> clusters = embeddingService.clusterSimilarErrors(embeddings);
        LOG.info(String.format("Stage 3 complete (%d clusters identified).", clusters.size()));

        // Build an ID-to-Entry index
        Map<String, LogEntry> entryIndex = entries.stream()
            .collect(Collectors.toMap(LogEntry::getId, entry -> entry));

        // --- Stage 4: Analyse each cluster ---
        LOG.info("Stage 4: LLM Root Cause Analysis...");
        List<AnalysisResult> results = new ArrayList<>();

        for (Map.Entry<String, List<String>> clusterEntry : clusters.entrySet()) {
            String clusterLabel = clusterEntry.getKey();
            List<String> memberIds = clusterEntry.getValue();

            // Extract the actual LogEntry objects
            List<LogEntry> clusterEntries = memberIds.stream()
                .map(entryIndex::get)
                .collect(Collectors.toList());

            if (clusterEntries.isEmpty()) continue;

            if (clusterEntries.size() < SMALL_CLUSTER_WARN_THRESHOLD) {
                LOG.fine("Cluster " + clusterLabel + " has only " + clusterEntries.size() + " entry - analysis may be low confidence.");
            }

            LOG.info(String.format("Analyzing cluster %s (%d entries)...", clusterLabel, clusterEntries.size()));

            AnalysisResult result = rcaService.analyzeCluster(clusterEntries);
            if (result != null) {
                result.setLogEntryId(clusterLabel + ":" + result.getLogEntryId());
                results.add(result);
            }
        }

        LOG.info("=== Pipeline Complete: " + results.size() + " result(s) produced ===");
        return results;
    }
}
