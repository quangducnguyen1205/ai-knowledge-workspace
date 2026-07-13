package com.aiknowledgeworkspace.workspacecore.processing.application;

public record ProcessingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
