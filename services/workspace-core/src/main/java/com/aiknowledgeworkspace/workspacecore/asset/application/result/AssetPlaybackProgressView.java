package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Product view of playback progress. {@code updatedAt} is null when the current user has never
 * saved progress for this Asset.
 */
public record AssetPlaybackProgressView(
        UUID assetId,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {

    public static AssetPlaybackProgressView unstarted(UUID assetId) {
        return new AssetPlaybackProgressView(assetId, 0L, false, null);
    }
}
