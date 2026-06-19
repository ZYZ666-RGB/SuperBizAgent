package org.example.rag.model;

public enum IndexStatus {
    UPLOADED,
    PARSING,
    PARSED,
    NORMALIZING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    COMPLETED,
    FAILED
}
