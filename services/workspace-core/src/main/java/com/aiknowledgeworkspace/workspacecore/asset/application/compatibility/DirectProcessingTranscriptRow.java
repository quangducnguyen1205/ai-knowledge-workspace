package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility;

public record DirectProcessingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
