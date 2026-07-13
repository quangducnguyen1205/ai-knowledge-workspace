package com.aiknowledgeworkspace.workspacecore.search.application;

public record IndexingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) implements TranscriptFingerprintRow {
}
