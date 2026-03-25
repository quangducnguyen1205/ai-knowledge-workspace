package com.aiknowledgeworkspace.workspacecore.asset;

public record AssetTranscriptRowResponse(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
