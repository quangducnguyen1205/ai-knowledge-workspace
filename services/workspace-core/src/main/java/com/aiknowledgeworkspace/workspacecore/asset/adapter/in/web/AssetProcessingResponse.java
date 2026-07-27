package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.YouTubeUrlPolicy;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.UUID;

public record AssetProcessingResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        UUID workspaceId,
        AssetSourceType sourceType,
        String youtubeVideoId,
        String sourceUrl
) {
    public static AssetProcessingResponse from(AssetProcessingResult result) {
        return new AssetProcessingResponse(
                result.assetId(),
                result.processingJobId(),
                result.status(),
                result.workspaceId(),
                result.sourceType(),
                result.youtubeVideoId(),
                YouTubeUrlPolicy.canonicalUrl(result.youtubeVideoId())
        );
    }
}
