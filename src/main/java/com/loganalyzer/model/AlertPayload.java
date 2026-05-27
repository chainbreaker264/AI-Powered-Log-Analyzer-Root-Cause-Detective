package com.loganalyzer.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alert payload dispatched when a critical error cluster is detected.
 * Can be sent to PagerDuty, Slack, or any webhook-compatible system.
 */
public class AlertPayload {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private Severity severity;
    private String summary;
    private List<String> affectedServices;
    private String llmSummary; // human-readable AI-generated summary
    private LocalDateTime detectedAt;

    public AlertPayload() {
        this.detectedAt = LocalDateTime.now();
    }

    public AlertPayload(Severity severity, String summary, 
                        List<String> affectedServices, String llmSummary) {
        this.severity = severity;
        this.summary = summary;
        this.affectedServices = affectedServices;
        this.llmSummary = llmSummary;
        this.detectedAt = LocalDateTime.now();
    }

    // --- Getters ---

    public Severity getSeverity() { return severity; }
    public String getSummary() { return summary; }
    public List<String> getAffectedServices() { return affectedServices; }
    public String getLlmSummary() { return llmSummary; }
    public LocalDateTime getDetectedAt() { return detectedAt; }

    // --- Setters ---

    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setAffectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; }
    public void setLlmSummary(String llmSummary) { this.llmSummary = llmSummary; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    @Override
    public String toString() {
        return String.format(
            "AlertPayload{severity=%s, summary='%s', services=%s, detectedAt=%s}",
            severity, summary, affectedServices, detectedAt);
    }
}
