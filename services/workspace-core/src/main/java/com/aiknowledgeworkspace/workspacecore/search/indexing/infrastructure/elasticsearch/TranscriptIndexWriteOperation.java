package com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.elasticsearch;

public record TranscriptIndexWriteOperation(String documentId, TranscriptIndexDocument document) {
}
