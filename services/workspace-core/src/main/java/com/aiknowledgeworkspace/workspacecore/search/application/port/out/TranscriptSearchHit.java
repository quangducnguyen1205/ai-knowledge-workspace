package com.aiknowledgeworkspace.workspacecore.search.application.port.out;

import java.util.UUID;

public record TranscriptSearchHit(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt,
        Double score
) {
}
