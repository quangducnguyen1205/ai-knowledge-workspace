package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;

import java.util.UUID;

public record AssetUploadResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        UUID workspaceId,
        AssetSourceType sourceType,
        String youtubeVideoId
) {
}
