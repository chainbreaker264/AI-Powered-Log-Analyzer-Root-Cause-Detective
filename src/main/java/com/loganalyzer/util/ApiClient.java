package com.loganalyzer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP client for the Anthropic Messages API.
 * Uses only java.net - no third-party HTTP libraries required.
 */
public class ApiClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 60000;

    private ApiClient() {}

    /**
     * Sends a prompt to the Anthropic Claude API and returns the raw JSON response body.
     * @param prompt the user-facing prompt text
     * @param apiKey Anthropic API key text
     * @param model model identifier, e.g., "claude-3-5-sonnet-20241022"
     * @param maxTokens maximum tokens to generate
     * @return response raw JSON response JSON string
     * @throws IOException on network or timeout failures
     */
    public static String postToAnthropic(String prompt, String apiKey, String model, int maxTokens) throws IOException {
        String requestBody = JsonUtil.buildAnthropicRequest(model, maxTokens, prompt);
        return post(ANTHROPIC_API_URL, apiKey, requestBody);
    }

    /**
     * Convenience overload using default model and token limit.
     */
    public static String postToAnthropic(String prompt, String apiKey) throws IOException {
        return postToAnthropic(prompt, apiKey, "claude-3-5-sonnet-20241022", 1024);
    }

    // --- Internal HTTP helpers ---

    private static String post(String urlString, String apiKey, String body) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
        conn.setRequestProperty("Accept", "application/json");

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int statusCode = conn.getResponseCode();
        if (statusCode >= 200 && statusCode < 300) {
            try (InputStream is = conn.getInputStream()) {
                return readStream(is);
            }
        } else {
            try (InputStream es = conn.getErrorStream()) {
                String errorBody = es != null ? readStream(es) : "(no response body)";
                throw new IOException("Anthropic API returned HTTP status " + statusCode + ": " + errorBody);
            }
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        byte[] buffer = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
