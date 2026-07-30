package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import java.time.Instant;
import java.util.UUID;

/** JPQL projection joining stored progress to the Asset that still owns it. */
public record ResumableAssetPlaybackRow(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        AssetSourceType sourceType,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {
}
