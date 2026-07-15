package org.example.dto;

import java.util.List;

public record ResearchProfilePayload(
        String domain,
        String researchObject,
        String researchProblem,
        String proposedMethod,
        List<String> variables,
        List<String> keywordsZh,
        List<String> keywordsEn,
        List<String> coreKeywords,
        List<String> methodKeywords,
        List<String> expandedKeywords,
        List<String> excludedKeywords,
        List<List<String>> keywordGroups,
        List<String> relatedFields,
        List<String> researchQuestions,
        boolean confirmed
) {
}
