package org.example.dto;

public record SourceReference(
        String titleOrFilename,
        String section,
        Integer page,
        String tableNumber,
        String evidenceLevel,
        String chunkType,
        String contentSnippet,
        float score,
        String sourcePath
) {
}
