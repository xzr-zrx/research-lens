package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import org.example.enums.ChatScope;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        @NotBlank String message,
        List<Map<String, String>> history,
        ChatScope scope,
        Long projectId,
        List<Long> paperIds
) {
}
