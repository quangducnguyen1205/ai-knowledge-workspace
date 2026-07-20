package com.aiknowledgeworkspace.workspacecore.processing.api;

public record ProcessingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
