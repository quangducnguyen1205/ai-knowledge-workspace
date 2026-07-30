package com.aiknowledgeworkspace.workspacecore.asset.application.model;

public record CanonicalTranscriptContextRow(
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
