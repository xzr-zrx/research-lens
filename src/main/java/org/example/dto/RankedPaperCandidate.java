package org.example.dto;

import java.util.List;

public record RankedPaperCandidate(
        PaperCandidate paper,
        double embeddingScore,
        double keywordScore,
        double titleScore,
        double qualityScore,
        double coreCoverageScore,
        double phraseGroupScore,
        double abstractMatchScore,
        double finalScore,
        List<String> matchedCoreKeywords,
        List<String> matchedMethodKeywords,
        List<List<String>> matchedKeywordGroups,
        String recommendationReason
) {
}
