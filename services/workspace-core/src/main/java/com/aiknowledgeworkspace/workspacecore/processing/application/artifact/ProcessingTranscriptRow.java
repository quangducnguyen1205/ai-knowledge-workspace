package com.aiknowledgeworkspace.workspacecore.processing.application.artifact;

public record ProcessingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
