package org.example.dto;

import java.util.List;

public record BatchKnowledgeRequest(List<Long> projectPaperIds) {
}
