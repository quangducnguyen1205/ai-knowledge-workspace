package com.aiknowledgeworkspace.workspacecore.asset.application.model;

public record AssetTranscriptRowView(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
