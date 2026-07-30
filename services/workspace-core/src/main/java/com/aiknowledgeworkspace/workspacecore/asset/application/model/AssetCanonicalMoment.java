package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import java.util.UUID;

/** Current canonical identity, presentation and timing for one transcript row of one Asset. */
public record AssetCanonicalMoment(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        AssetSourceType sourceType,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text
) {
}
