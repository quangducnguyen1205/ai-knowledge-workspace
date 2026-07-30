package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record SavedMomentResponse(
        UUID savedMomentId,
        UUID workspaceId,
        UUID assetId,
        String assetTitle,
        String sourceType,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        Instant savedAt
) {
}
