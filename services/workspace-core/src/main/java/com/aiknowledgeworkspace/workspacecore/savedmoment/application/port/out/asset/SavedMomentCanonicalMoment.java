package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset;

import java.util.UUID;

/** Current canonical presentation and navigation data for one saved moment. */
public record SavedMomentCanonicalMoment(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        String sourceType,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text
) {
}
