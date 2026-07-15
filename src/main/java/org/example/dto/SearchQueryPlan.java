package org.example.dto;

import org.example.enums.QueryType;

public record SearchQueryPlan(QueryType type, String query) {
}
