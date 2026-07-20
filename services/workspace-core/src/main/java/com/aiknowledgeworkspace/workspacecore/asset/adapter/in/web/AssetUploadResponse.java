package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import java.util.UUID;

public record AssetUploadResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        UUID workspaceId
) {
}
