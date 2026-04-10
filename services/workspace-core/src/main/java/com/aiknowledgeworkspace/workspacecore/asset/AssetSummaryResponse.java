package com.aiknowledgeworkspace.workspacecore.asset;

import java.time.Instant;
import java.util.UUID;

public record AssetSummaryResponse(
        UUID assetId,
        String title,
        AssetStatus assetStatus,
        UUID workspaceId,
        Instant createdAt
) {
}
