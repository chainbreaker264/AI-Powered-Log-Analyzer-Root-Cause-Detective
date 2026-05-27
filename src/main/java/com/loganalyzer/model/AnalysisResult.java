package com.loganalyzer.model;

import java.time.LocalDateTime;

/**
 * Holds the LLM-generated root cause analysis for a cluster of similar log entries.
 */
public class AnalysisResult {

    private String logEntryId;         // representative entry ID from the cluster
    private String errorPattern;       // short description of the error pattern
    private String rootCause;          // LLM-inferred root cause
    private String fixSuggestion;      // actionable remediation step
    private double confidenceScore;    // 0.0 - 1.0 confidence from the LLM response
    private LocalDateTime analyzedAt;

    public AnalysisResult() {
        this.analyzedAt = LocalDateTime.now();
    }

    public AnalysisResult(String logEntryId, String errorPattern, String rootCause, 
                          String fixSuggestion, double confidenceScore) {
        this.logEntryId = logEntryId;
        this.errorPattern = errorPattern;
        this.rootCause = rootCause;
        this.fixSuggestion = fixSuggestion;
        this.confidenceScore = confidenceScore;
        this.analyzedAt = LocalDateTime.now();
    }

    // --- Getters ---

    public String getLogEntryId() { return logEntryId; }
    public String getErrorPattern() { return errorPattern; }
    public String getRootCause() { return rootCause; }
    public String getFixSuggestion() { return fixSuggestion; }
    public double getConfidenceScore() { return confidenceScore; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }

    // --- Setters ---

    public void setLogEntryId(String logEntryId) { this.logEntryId = logEntryId; }
    public void setErrorPattern(String errorPattern) { this.errorPattern = errorPattern; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public void setFixSuggestion(String fixSuggestion) { this.fixSuggestion = fixSuggestion; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    @Override
    public String toString() {
        return String.format(
            "AnalysisResult{logEntryId='%s', pattern='%s', rootCause='%s', fix='%s', confidence=%.2f, at='%s'}",
            logEntryId, errorPattern, rootCause, fixSuggestion, confidenceScore, analyzedAt);
    }
}
