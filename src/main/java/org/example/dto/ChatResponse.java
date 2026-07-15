package org.example.dto;

import java.util.List;

public record ChatResponse(String answer, List<SourceReference> sources, ChatDiagnostics diagnostics) {
}
