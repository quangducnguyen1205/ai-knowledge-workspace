package com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing;

public record TranscriptIndexWriteOperation(String documentId, TranscriptIndexDocument document) {
}
