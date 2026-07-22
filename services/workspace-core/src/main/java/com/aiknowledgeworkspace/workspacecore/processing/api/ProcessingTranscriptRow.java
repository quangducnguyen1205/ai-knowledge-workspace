package com.aiknowledgeworkspace.workspacecore.processing.api;

public record ProcessingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
