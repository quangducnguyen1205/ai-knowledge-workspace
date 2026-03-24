package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.UUID;

public record AssetUploadResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus
) {
}

