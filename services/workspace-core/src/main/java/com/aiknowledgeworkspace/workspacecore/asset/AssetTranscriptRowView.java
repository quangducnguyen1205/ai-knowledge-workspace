package com.aiknowledgeworkspace.workspacecore.asset;

public record AssetTranscriptRowView(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
