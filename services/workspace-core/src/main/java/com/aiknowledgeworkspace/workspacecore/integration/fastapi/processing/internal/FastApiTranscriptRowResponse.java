package com.aiknowledgeworkspace.workspacecore.integration.fastapi.processing.internal;

public record FastApiTranscriptRowResponse(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
