package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import java.util.UUID;

public record AssetUploadResult(
        UUID assetId,
        UUID processingJobId,
        AssetStatus status,
        UUID workspaceId,
        AssetSourceType sourceType,
        String youtubeVideoId
) {
}
