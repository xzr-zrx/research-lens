package org.example.enums;

import java.time.Year;

/**
 * 论文检索时间范围预设。
 */
public enum PaperTimePreset {
    RECENT_3_YEARS,
    RECENT_5_YEARS,
    SINCE_2020,
    CUSTOM,
    ALL_TIME;

    /**
     * 根据预设和自定义值计算实际起止年份。
     *
     * @param customStart 自定义开始年份（仅 CUSTOM 时使用）
     * @param customEnd   自定义结束年份（仅 CUSTOM 时使用）
     * @return {startYear, endYear}，ALL_TIME 时为 {null, null}
     */
    public static Integer[] resolveYears(PaperTimePreset preset, Integer customStart, Integer customEnd) {
        if (preset == null) preset = ALL_TIME;
        int currentYear = Year.now().getValue();
        return switch (preset) {
            case RECENT_3_YEARS -> new Integer[]{currentYear - 2, currentYear};
            case RECENT_5_YEARS -> new Integer[]{currentYear - 4, currentYear};
            case SINCE_2020 -> new Integer[]{2020, currentYear};
            case CUSTOM -> new Integer[]{customStart, customEnd};
            case ALL_TIME -> new Integer[]{null, null};
        };
    }
}
