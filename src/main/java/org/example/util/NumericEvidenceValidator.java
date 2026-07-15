package org.example.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数值证据校验：回答中出现的具体数字必须能在检索片段原文中找到，
 * 否则视为模型编造或推导。
 *
 * 默认禁止计算：派生数据（如由 2× 推出 +100%、减少 50%）无法在原文中找到，
 * 会被标记为未验证。
 */
public final class NumericEvidenceValidator {
    private NumericEvidenceValidator() {}

    /** 匹配整数、小数、百分比、科学计数法；忽略纯年份（1000-2099 单独出现时不报）。 */
    private static final Pattern NUMBER = Pattern.compile(
            "(?<!\\w)(\\d+\\.\\d+|\\d+)(?:\\s*%|\\s*‰)?(?![\\d\\.])(?:\\s*[×x*]\\s*10\\^?-?\\d+)?");

    private static final Pattern YEAR = Pattern.compile("\\b(19[5-9]\\d|20[0-9]\\d)\\b");

    /** 从回答中提取需要校验的数值（去掉明显是年份的）。 */
    public static List<String> extractNumbers(String answer) {
        Set<String> numbers = new LinkedHashSet<>();
        if (answer == null || answer.isBlank()) return new ArrayList<>();
        Set<String> years = new LinkedHashSet<>();
        Matcher ym = YEAR.matcher(answer);
        while (ym.find()) years.add(ym.group(1));

        Matcher m = NUMBER.matcher(answer);
        while (m.find()) {
            String raw = m.group().trim();
            String core = raw.replaceAll("[%‰\\s×x*10^]", "").replaceFirst("\\.$", "");
            if (core.isEmpty()) continue;
            if (years.contains(core)) continue;
            if (core.length() > 1 && core.startsWith("0") && !core.contains(".")) continue;
            numbers.add(core);
        }
        return new ArrayList<>(numbers);
    }

    /** 归一化：压缩空白用于宽松子串匹配。 */
    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 返回回答中未能在任何原文片段中找到的数值。
     * 匹配规则：数值的整数部分或完整小数作为子串出现在某片段中。
     */
    public static List<String> findUnverified(String answer, List<String> sourceSnippets) {
        List<String> unverified = new ArrayList<>();
        List<String> numbers = extractNumbers(answer);
        if (numbers.isEmpty()) return unverified;

        List<String> normalizedSources = new ArrayList<>();
        if (sourceSnippets != null) {
            for (String s : sourceSnippets) normalizedSources.add(normalize(s));
        }

        for (String number : numbers) {
            if (matchesAny(number, normalizedSources)) continue;
            unverified.add(number);
        }
        return unverified;
    }

    private static boolean matchesAny(String number, List<String> sources) {
        for (String source : sources) {
            if (source.isEmpty()) continue;
            if (source.contains(number)) return true;
            int dot = number.indexOf('.');
            if (dot > 0 && source.contains(number.substring(0, dot))) return true;
            String compact = number.replace(".", "");
            if (source.replace(".", "").contains(compact)) return true;
        }
        return false;
    }
}
