package com.aiknowledgeworkspace.workspacecore.asset;

public record AssetTranscriptRowInput(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
