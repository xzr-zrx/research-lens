package org.example.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TitleNormalizer {
    private TitleNormalizer() {}

    public static String normalize(String title) {
        if (title == null) return "";
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("https?://(dx\\.)?doi\\.org/", "")
                .replace('-', ' ')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public static double similarity(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        if (x.equals(y)) return 1.0;
        String[] xs = x.split(" ");
        String[] ys = y.split(" ");
        long intersection = java.util.Arrays.stream(xs).distinct()
                .filter(token -> java.util.Arrays.asList(ys).contains(token)).count();
        long union = java.util.stream.Stream.concat(java.util.Arrays.stream(xs), java.util.Arrays.stream(ys)).distinct().count();
        return union == 0 ? 0.0 : (double) intersection / union;
    }
}
