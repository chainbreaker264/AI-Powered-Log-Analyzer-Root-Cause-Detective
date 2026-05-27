package com.loganalyzer.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight JSON serializer / parser using only the Java standard library.
 * Handles the subset of JSON needed by this application: flat objects,
 * string/number/boolean fields, and simple string arrays.
 * For production use with complex schemas, replace with Jackson or Gson.
 */
public class JsonUtil {

    private JsonUtil() {}

    // --- Serialization ---

    /**
     * Converts a Map<String, Object> to a JSON object string.
     * Supports nested Lists/Maps recursively.
     */
    public static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts a List<String> to a JSON array string.
     */
    public static String listToJson(List<String> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeString(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return escapeString((String) value);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(valueToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return mapToJson(map);
        }
        return escapeString(value.toString());
    }

    private static String escapeString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t") + "\"";
    }

    // --- Parsing helpers - extract values from a raw JSON string ---

    /**
     * Extracts the string value of a top-level JSON field.
     * e.g., extractString("{\"key\": \"value\"}", "key") -> "value"
     */
    public static String extractString(String json, String fieldName) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:[^\\\\\"]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1);
            return val.replace("\\\"", "\"")
                      .replace("\\\\", "\\")
                      .replace("\\n", "\n")
                      .replace("\\r", "\r")
                      .replace("\\t", "\t");
        }
        return null;
    }

    /**
     * Extracts a numeric value for a top-level JSON field.
     */
    public static String extractNumber(String json, String fieldName) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Extracts an array of strings from a top-level JSON field.
     * e.g., extractStringArray("{\"days\": [\"mon\", \"tue\"]}", "days") -> ["mon", "tue"]
     */
    public static List<String> extractStringArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String arrayContent = m.group(1);
            Pattern strPattern = Pattern.compile("\"((?:[^\\\\\"]|\\\\.)*)\"");
            Matcher strMatcher = strPattern.matcher(arrayContent);
            while (strMatcher.find()) {
                String s = strMatcher.group(1);
                result.add(s.replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        }
        return result;
    }

    /**
     * Parses the first-level key-value pairs from a simple flat JSON object
     * into a Map<String, String>. Values are always coerced to strings.
     */
    public static Map<String, String> parseSimpleObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;

        // Pattern matches "key" : "value" or "key" : number/boolean/null
        Pattern p = Pattern.compile("\"([a-zA-Z0-9_-]+)\"\\s*:\\s*(?:\"((?:[^\\\\\"]|\\\\.)*)\"|([^,}\\s]+))");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String strVal = m.group(2);
            String rawVal = m.group(3);
            
            String value = (strVal != null) ? strVal : rawVal;
            if (value != null) {
                value = value.trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                // Unescape basic sequences if it was a string
                if (strVal != null) {
                    value = value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
                }
            }
            result.put(key, "null".equals(value) ? null : value);
        }
        return result;
    }

    /**
     * Builds a minimal Anthropic Messages API request body.
     */
    public static String buildAnthropicRequest(String model, int maxTokens, String userPrompt) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("max_tokens", maxTokens);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", userPrompt);
        messages.add(msg);
        
        req.put("messages", messages);
        return mapToJson(req);
    }

    /**
     * Extracts the assistant's text reply from an Anthropic API response.
     * The response shape is: {"content": [{"type": "text", "text": "..."}], ...}
     */
    public static String extractAssistantReply(String responseJson) {
        if (responseJson == null) return null;
        // Navigate into the content array and grab the first text block
        Pattern textBlockPattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\\\\\"]|\\\\.)*)\"");
        Matcher m = textBlockPattern.matcher(responseJson);
        if (m.find()) {
            String text = m.group(1);
            return text.replace("\\\"", "\"")
                       .replace("\\\\", "\\")
                       .replace("\\n", "\n")
                       .replace("\\t", "\t")
                       .replace("\\r", "\r");
        }
        // Fallback: try extracting "text" field directly
        return extractString(responseJson, "text");
    }
}
