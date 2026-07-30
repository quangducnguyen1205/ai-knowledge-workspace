package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import java.time.Instant;
import java.util.UUID;

/**
 * One resumable Asset for the current user. Title and source are projected from current Asset
 * state rather than from a stored presentation snapshot, so a rename is visible on the next read.
 */
public record ResumableAssetPlayback(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        AssetSourceType sourceType,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {
}
