package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.UUID;

public record AssetProcessingResult(
        UUID assetId,
        UUID processingJobId,
        AssetStatus status,
        UUID workspaceId,
        AssetSourceType sourceType,
        String youtubeVideoId
) {
}
