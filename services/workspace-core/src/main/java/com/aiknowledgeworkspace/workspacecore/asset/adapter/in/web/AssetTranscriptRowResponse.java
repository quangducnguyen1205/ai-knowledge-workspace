package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

public record AssetTranscriptRowResponse(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
