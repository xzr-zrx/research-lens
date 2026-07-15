package org.example.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JsonUtils {
    private JsonUtils() {}

    public static String extractJsonObject(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return cleaned;
    }

    public static String extractJsonArray(String text) {
        if (text == null) return "[]";
        String cleaned = text.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return cleaned;
    }

    public static List<String> readStringList(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<List<String>> readNestedStringList(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<List<String>> nestedStringArray(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) return Collections.emptyList();
        List<List<String>> result = new ArrayList<>();
        value.forEach(group -> {
            if (!group.isArray()) return;
            List<String> items = new ArrayList<>();
            group.forEach(item -> {
                String s = item.asText("").trim();
                if (!s.isBlank() && !items.contains(s)) items.add(s);
            });
            if (items.size() >= 2) result.add(items);
        });
        return result;
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public static List<String> stringArray(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            String s = item.asText("").trim();
            if (!s.isBlank() && !result.contains(s)) result.add(s);
        });
        return result;
    }
}
