package org.example.enums;

public enum KnowledgeStatus {
    NOT_INDEXED,
    FETCHING_FULL_TEXT,
    PARSING_FULL_TEXT,
    INDEXING,
    ABSTRACT_ONLY,
    FULL_TEXT_INDEXED,
    MANUAL_UPLOAD_REQUIRED,
    FAILED
}
