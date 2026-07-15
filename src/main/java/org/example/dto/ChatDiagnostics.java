package org.example.dto;

import java.util.List;

public record ChatDiagnostics(
        int abstractPaperCount,
        int fullTextPaperCount,
        int fullTextChunkCount,
        int abstractChunkHits,
        int fullTextChunkHits,
        int tableChunkHits,
        boolean dataQuery,
        boolean fullTextAvailable,
        List<String> unverifiedNumbers
) {
}
