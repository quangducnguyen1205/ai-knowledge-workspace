package com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing;

import java.util.UUID;

public record TranscriptIndexDocument(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt,
        String assetStatus
) {
}
