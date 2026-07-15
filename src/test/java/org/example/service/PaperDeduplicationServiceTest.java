package org.example.service;

import org.example.dto.PaperCandidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperDeduplicationServiceTest {
    @Test
    void mergesArxivAndOpenAlexVersions() {
        PaperCandidate a = new PaperCandidate();
        a.setTitle("Blind PTF Calibration for Phase Imaging");
        a.setDoi("10.1000/test");
        a.setSources(new ArrayList<>(List.of("OpenAlex")));

        PaperCandidate b = new PaperCandidate();
        b.setTitle("Blind PTF Calibration for Phase-Imaging");
        b.setDoi("10.1000/test");
        b.setArxivId("2601.00001");
        b.setSources(new ArrayList<>(List.of("arXiv")));

        var result = new PaperDeduplicationService().deduplicate(List.of(a, b));
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getSources().size());
    }
}
