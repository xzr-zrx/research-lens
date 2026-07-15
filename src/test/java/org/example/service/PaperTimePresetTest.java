package org.example.service;

import org.example.enums.PaperTimePreset;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;

class PaperTimePresetTest {

    @Test
    void recent3YearsComputes() {
        Integer[] result = PaperTimePreset.resolveYears(PaperTimePreset.RECENT_3_YEARS, null, null);
        int current = Year.now().getValue();
        assertEquals(current - 2, result[0]);
        assertEquals(current, result[1]);
    }

    @Test
    void recent5YearsComputes() {
        Integer[] result = PaperTimePreset.resolveYears(PaperTimePreset.RECENT_5_YEARS, null, null);
        int current = Year.now().getValue();
        assertEquals(current - 4, result[0]);
        assertEquals(current, result[1]);
    }

    @Test
    void since2020Computes() {
        Integer[] result = PaperTimePreset.resolveYears(PaperTimePreset.SINCE_2020, null, null);
        int current = Year.now().getValue();
        assertEquals(2020, result[0]);
        assertEquals(current, result[1]);
    }

    @Test
    void customReturnsExactValues() {
        Integer[] result = PaperTimePreset.resolveYears(PaperTimePreset.CUSTOM, 2023, 2025);
        assertEquals(2023, result[0]);
        assertEquals(2025, result[1]);
    }

    @Test
    void allTimeReturnsNulls() {
        Integer[] result = PaperTimePreset.resolveYears(PaperTimePreset.ALL_TIME, null, null);
        assertNull(result[0]);
        assertNull(result[1]);
    }

    @Test
    void nullPresetDefaultsToAllTime() {
        Integer[] result = PaperTimePreset.resolveYears(null, null, null);
        assertNull(result[0]);
        assertNull(result[1]);
    }

    @Test
    void yearsAreWithinReasonableRange() {
        int current = Year.now().getValue();
        for (PaperTimePreset preset : PaperTimePreset.values()) {
            if (preset == PaperTimePreset.ALL_TIME) continue;
            if (preset == PaperTimePreset.CUSTOM) continue;
            Integer[] result = PaperTimePreset.resolveYears(preset, null, null);
            assertTrue(result[0] >= 1900, preset + " start too early: " + result[0]);
            assertTrue(result[1] <= 2100, preset + " end too far: " + result[1]);
            assertTrue(result[0] <= result[1], preset + " start > end: " + result[0] + " > " + result[1]);
        }
    }
}
