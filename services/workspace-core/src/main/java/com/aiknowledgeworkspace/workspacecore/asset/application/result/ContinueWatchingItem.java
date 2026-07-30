package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import java.time.Instant;
import java.util.UUID;

public record ContinueWatchingItem(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        String sourceType,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {
}
