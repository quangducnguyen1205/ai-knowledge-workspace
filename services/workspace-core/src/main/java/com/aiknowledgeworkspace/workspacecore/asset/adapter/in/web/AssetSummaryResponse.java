package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

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
