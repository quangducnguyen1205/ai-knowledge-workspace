package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

public record AssetTranscriptRowResponse(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
