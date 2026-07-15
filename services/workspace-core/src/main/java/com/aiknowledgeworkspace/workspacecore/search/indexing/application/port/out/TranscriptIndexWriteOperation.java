package com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out;

public record TranscriptIndexWriteOperation(String documentId, TranscriptIndexDocument document) {
}
