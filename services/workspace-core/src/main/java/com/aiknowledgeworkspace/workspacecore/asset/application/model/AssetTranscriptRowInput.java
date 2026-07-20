package com.aiknowledgeworkspace.workspacecore.asset.application.model;

public record AssetTranscriptRowInput(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
