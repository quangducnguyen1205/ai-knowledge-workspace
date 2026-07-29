package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import java.time.Instant;

/**
 * Persisted playback progress for one user and Asset. The owning user identity stays an
 * argument of the store port; it is never carried into a public response.
 */
public record AssetPlaybackProgressSnapshot(
        long positionMs,
        boolean completed,
        Instant updatedAt
) {
}
