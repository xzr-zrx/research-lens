package org.example.util;

public final class EvidenceValidator {
    private EvidenceValidator() {}

    public static boolean isDirectSubstring(String evidence, String source) {
        if (evidence == null || source == null) return false;
        String e = normalize(evidence);
        String s = normalize(source);
        return !e.isBlank() && s.contains(e);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
