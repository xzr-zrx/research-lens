package org.example.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.TreeMap;

public final class AbstractInvertedIndexConverter {
    private AbstractInvertedIndexConverter() {}

    public static String convert(JsonNode invertedIndex) {
        if (invertedIndex == null || !invertedIndex.isObject()) return "";
        Map<Integer, String> positions = new TreeMap<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            String word = entry.getKey();
            JsonNode arr = entry.getValue();
            if (arr != null && arr.isArray()) {
                arr.forEach(pos -> positions.put(pos.asInt(), word));
            }
        });
        return String.join(" ", positions.values()).trim();
    }
}
