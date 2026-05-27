package com.loganalyzer.cli;

import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.LogAnalysisOrchestrator;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Command-line entry point for the AI-Powered Log Analyzer.
 * 
 * Usage:
 * java -cp output:lib/* com.loganalyzer.cli.LogAnalyzerCLI <logfile> [options]
 * 
 * Options:
 * --api-key <key>   Override the Anthropic API key from application.properties
 * --no-ai           Run heuristic-only analysis (no Anthropic API call)
 * --help            Show this help message
 */
public class LogAnalyzerCLI {

    private static final String PROPERTIES_FILE = "application.properties";
    private static final String FINAL_BANNER = 
            "=============================================================\n" +
            "    AI-Powered Log Analyzer - Root Cause Detective          \n" +
            "              Powered by Anthropic Claude                     \n" +
            "=============================================================\n";

    public static void main(String[] args) throws Exception {
        System.out.print(FINAL_BANNER);

        // Parse arguments
        String logFilePath = null;
        String apiKeyOverride = null;
        boolean noAi = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                    printHelp();
                    return;
                case "--api-key":
                    if (i + 1 < args.length) {
                        apiKeyOverride = args[++i];
                    }
                    break;
                case "--no-ai":
                    noAi = true;
                    break;
                default:
                    if (!args[i].startsWith("-")) {
                        logFilePath = args[i];
                    }
            }
        }

        if (logFilePath == null) {
            System.err.println("ERROR: No log file specified.");
            printHelp();
            System.exit(1);
        }

        // Load configuration
        Properties props = loadProperties();
        String apiKey = apiKeyOverride != null ? apiKeyOverride : props.getProperty("anthropic.api.key", "");

        if (noAi) {
            System.out.println("[INFO] Running in OFFLINE / HEURISTIC mode (No Anthropic API call).");
            apiKey = "OFFLINE";
        }

        // Read log file
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(logFilePath), StandardCharsets.UTF_8);
            System.out.println("[INFO] Loaded " + lines.size() + " lines from " + logFilePath);
        } catch (IOException e) {
            System.err.println("ERROR: Cannot read log file: " + logFilePath + " -> " + e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("[INFO] Starting analysis pipeline...");
        long startMs = System.currentTimeMillis();

        LogAnalysisOrchestrator orchestrator = new LogAnalysisOrchestrator(apiKey);
        List<AnalysisResult> results;
        try {
            results = orchestrator.analyzeLogBatch(lines);
        } catch (Exception e) {
            System.err.println("ERROR during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startMs;

        // Print results
        printResults(results, elapsedMs);
    }

    // Output rendering
    private static void printResults(List<AnalysisResult> results, long elapsedMs) {
        System.out.println("\n" + "=".repeat(70));
        System.out.printf(" ANALYSIS COMPLETE - found %d cluster(s) [%.2f s]%n",
                results.size(), (elapsedMs / 1000.0));
        System.out.println("=".repeat(70));

        if (results.isEmpty()) {
            System.out.println("\n No error clusters detected. Logs appear healthy.\n");
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            System.out.println("\n" + "-".repeat(70));
            System.out.println(" Cluster " + (i + 1) + " of " + results.size());
            printCluster(i + 1, results.get(i));
        }
        System.out.println("\n" + "=".repeat(70));
        System.out.println(" Full analysis written above. Review each cluster and apply suggested fixes.");
        System.out.println("=".repeat(70) + "\n");
    }

    private static void printCluster(int number, AnalysisResult result) {
        System.out.println("-".repeat(70));
        System.out.println(" Pattern   : " + wrap(result.getErrorPattern(), 52));
        System.out.println(" Root Cause : " + wrap(result.getRootCause(), 52));
        System.out.println(" Fix Sugges.: " + wrap(result.getFixSuggestion(), 52));
        System.out.printf( " Confidence : %.0f%%%n", result.getConfidenceScore() * 100);
        System.out.println(" Analyzed At: " + result.getAnalyzedAt());
        System.out.println("-".repeat(70));
    }

    /**
     * Wraps long strings at word boundaries for clean terminal output.
     */
    private static String wrap(String text, int width) {
        if (text == null) return "N/A";
        if (text.length() <= width) return text;

        StringBuilder out = new StringBuilder();
        String indent = "             ";
        int pos = 0;
        boolean first = true;

        while (pos < text.length()) {
            int end = Math.min(pos + width, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > pos) {
                    end = space;
                }
            }
            if (!first) out.append("\n").append(indent);
            out.append(text.substring(pos, end).trim());
            pos = end + 1;
            first = false;
        }
        return out.toString().stripTrailing();
    }

    // Config helpers
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
            props.load(input);
        } catch (IOException e) {
            try (InputStream input = LogAnalyzerCLI.class.getClassLoader()
                    .getResourceAsStream(PROPERTIES_FILE)) {
                if (input != null) props.load(input);
            } catch (IOException ignored) {}
        }
        return props;
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -cp output:lib/* com.loganalyzer.cli.LogAnalyzerCLI <logfile> [options]");
        System.out.println("\nArguments:");
        System.out.println("  <logfile>        Path to the log file to analyze");
        System.out.println("\nOptions:");
        System.out.println("  --api-key <key>  Override Anthropic API key");
        System.out.println("  --no-ai          Heuristic-only mode (no API calls)");
        System.out.println("  --help           Show this help message");
        System.out.println("\nExamples:");
        System.out.println("  java -cp output:lib/* com.loganalyzer.cli.LogAnalyzerCLI example-logs.txt");
        System.out.println("  java -cp output:lib/* com.loganalyzer.cli.LogAnalyzerCLI app.log --api-key sk-ant-xxx");
    }
}
