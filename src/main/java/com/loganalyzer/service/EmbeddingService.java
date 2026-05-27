package com.loganalyzer.service;

import com.loganalyzer.model.LogEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates lightweight pseudo-embedding vectors for log messages and
 * clusters them by cosine similarity.
 */
public class EmbeddingService {

    private static final int VECTOR_DIM = 128;
    private static final double SIMILARITY_THRESHOLD = 0.85;

    // --- Public API ---

    /**
     * Embeds every log entry's message and returns a map from entry ID to vector.
     * @param entries list of parsed log entries
     * @return map: entryId -> float[] embedding vector
     */
    public Map<String, float[]> embedLogEntries(List<LogEntry> entries) {
        Map<String, float[]> result = new HashMap<>();
        for (LogEntry entry : entries) {
            if (entry == null || entry.getId() == null) continue;
            result.put(entry.getId(), embedMessage(entry.getMessage()));
        }
        return result;
    }

    /**
     * Clusters similar entries using single-linkage grouping.
     * @param embeddings map: entryId -> vector (output of embedLogEntries)
     * @return map: clusterLabel -> List of entry IDs belonging to that cluster
     */
    public Map<String, List<String>> clusterSimilarErrors(Map<String, float[]> embeddings) {
        List<String> ids = new ArrayList<>(embeddings.keySet());
        int[] labels = new int[ids.size()];
        
        // Initialize: each entry is its own cluster
        for (int i = 0; i < ids.size(); i++) labels[i] = i;

        // Single-pass greedy merging
        for (int i = 0; i < ids.size(); i++) {
            float[] v1 = embeddings.get(ids.get(i));
            for (int j = i + 1; j < ids.size(); j++) {
                float[] v2 = embeddings.get(ids.get(j));
                if (cosineSimilarity(v1, v2) >= SIMILARITY_THRESHOLD) {
                    // Merge cluster j into cluster i
                    int oldLabel = labels[j];
                    int newLabel = labels[i];
                    for (int k = 0; k < labels.length; k++) {
                        if (labels[k] == oldLabel) labels[k] = newLabel;
                    }
                }
            }
        }

        // Build cluster map keyed by "Cluster-N"
        Map<String, List<String>> clusters = new HashMap<>();
        int clusterNumber = 1;
        Map<Integer, String> labelToClusterName = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) {
            int lbl = labels[i];
            String clusterLabel = labelToClusterName.get(lbl);
            if (clusterLabel == null) {
                clusterLabel = "Cluster-" + clusterNumber++;
                labelToClusterName.put(lbl, clusterLabel);
            }
            clusters.computeIfAbsent(clusterLabel, k -> new ArrayList<>()).add(ids.get(i));
        }

        return clusters;
    }

    /**
     * Computes the cosine similarity between two float vectors.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0;
        double magA = 0.0;
        double magB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }

        double denom = Math.sqrt(magA) * Math.sqrt(magB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    // --- Private embedding implementation ---

    /**
     * Hashes character bi-grams into a fixed-size frequency vector.
     */
    private float[] embedMessage(String message) {
        float[] vector = new float[VECTOR_DIM];
        if (message == null || message.isBlank()) return vector;

        // Normalize: lowercase, collapse numbers/hex
        String normalised = message.toLowerCase()
            .replaceAll("0x[0-9a-f]+", "<HEX>")
            .replaceAll("\\d+", "<NUM>")
            .replaceAll("[^a-z<>]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        // Slide a bi-gram window and accumulate
        for (int i = 0; i < normalised.length() - 1; i++) {
            int c1 = normalised.charAt(i);
            int c2 = normalised.charAt(i + 1);
            int idx = Math.abs((c1 * 31 + c2) ^ 17) % VECTOR_DIM;
            vector[idx] += 1.0f;
        }

        // Include individual character counts
        for (int i = 0; i < normalised.length(); i++) {
            int idx = Math.abs(normalised.charAt(i) * 13) % VECTOR_DIM;
            vector[idx] += 0.5f;
        }

        // L2-normalize
        double magnitude = 0.0;
        for (float v : vector) magnitude += v * v;
        magnitude = Math.sqrt(magnitude);

        if (magnitude > 0.0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / magnitude);
            }
        }

        return vector;
    }
}
