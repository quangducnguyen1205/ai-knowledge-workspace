package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

public record SearchCanonicalContextRow(
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
