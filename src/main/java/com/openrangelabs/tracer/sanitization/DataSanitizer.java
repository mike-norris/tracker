package com.openrangelabs.tracer.sanitization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DataSanitizer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{3}-\\d{4}\\b");

    public JsonNode sanitize(JsonNode data) {
        if (data == null) return null;

        ObjectNode sanitized = data.deepCopy();
        sanitizeNode(sanitized);
        return sanitized;
    }

    private void sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            objNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase();
                if (key.contains("password") || key.contains("secret") || key.contains("token")) {
                    objNode.put(entry.getKey(), "[REDACTED]");
                } else if (entry.getValue().isTextual()) {
                    String text = entry.getValue().asText();
                    text = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL]");
                    text = PHONE_PATTERN.matcher(text).replaceAll("[PHONE]");
                    objNode.put(entry.getKey(), text);
                }
            });
        }
    }
}
