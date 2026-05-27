package com.loganalyzer.controller;

import com.loganalyzer.model.AnalysisResult;
import com.loganalyzer.LogAnalysisOrchestrator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP server exposing the log analyzer as a REST API.
 * Uses the JDK's built-in com.sun.net.httpserver.HttpServer - zero extra dependencies.
 */
public class LogAnalyzerHttpServer {
    private static final Logger LOG = Logger.getLogger(LogAnalyzerHttpServer.class.getName());
    private final int port;
    private final String apiKey;
    private HttpServer server;

    public LogAnalyzerHttpServer(int port, String apiKey) {
        this.port = port;
        this.apiKey = apiKey;
    }

    // Lifecycle
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/analyze", this::handleAnalyze);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(2);
    }

    // Handlers
    private void handleHealth(HttpExchange exchange) throws IOException {
        String body = "{\"status\":\"UP\",\"service\":\"log-analyzer\"}";
        sendJson(exchange, 200, body);
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) && exchange.getRequestURI().getPath().endsWith("/demo")) {
            handleDemo(exchange);
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        String requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (requestBody.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Request body must contain log lines\"}");
            return;
        }

        List<String> lines = Arrays.asList(requestBody.split("\\r?\\n"));
        LOG.info("POST /api/analyze -> " + lines.size() + " lines received");

        LogAnalysisOrchestrator orchestrator = new LogAnalysisOrchestrator(apiKey);
        try {
            List<AnalysisResult> results = orchestrator.analyzeLogBatch(lines);
            String json = resultsToJson(results);
            sendJson(exchange, 200, json);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"Analysis failed\", \"detail\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Analysis failed", e);
            sendJson(exchange, 500, "{\"error\":\"Internal server error\", \"detail\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleDemo(HttpExchange exchange) throws IOException {
        List<String> demoLogs = List.of(
                "2024-01-15 10:23:45 ERROR OrderService - Connection refused: JDBC connection error!",
                "2024-01-15 10:23:46 ERROR OrderService - Connection refused: JDBC connection error!",
                "2024-01-15 10:24:10 WARN  PaymentService - Payment Gateway Timeout after 5000ms.",
                "2024-01-15 10:25:01 INFO  HealthController - Health check - System healthy.",
                "2024-01-15 10:26:12 ERROR UserService - NullPointerException at UserService.java:142"
        );
        LOG.info("GET /api/analyze/demo -> Running analysis on " + demoLogs.size() + " hardcoded logs");
        LogAnalysisOrchestrator orchestrator = new LogAnalysisOrchestrator(apiKey);
        try {
            List<AnalysisResult> results = orchestrator.analyzeLogBatch(demoLogs);
            String json = resultsToJson(results);
            sendJson(exchange, 200, json);
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"Demo failed\", \"detail\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // Serialization
    private String resultsToJson(List<AnalysisResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < results.size(); i++) {
            AnalysisResult r = results.get(i);
            sb.append("{");
            sb.append("\"errorPattern\":\"").append(escape(r.getErrorPattern())).append("\",");
            sb.append("\"rootCause\":\"").append(escape(r.getRootCause())).append("\",");
            sb.append("\"fixSuggestion\":\"").append(escape(r.getFixSuggestion())).append("\",");
            sb.append("\"confidenceScore\":").append(r.getConfidenceScore()).append(",");
            sb.append("\"analyzedAt\":\"").append(r.getAnalyzedAt()).append("\"");
            sb.append("}");
            if (i < results.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // HTTP helpers
    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
