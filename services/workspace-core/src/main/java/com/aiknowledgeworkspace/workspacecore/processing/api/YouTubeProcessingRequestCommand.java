package com.aiknowledgeworkspace.workspacecore.processing.api;

import java.util.UUID;

public record YouTubeProcessingRequestCommand(
        UUID assetId,
        UUID workspaceId,
        String ownerId,
        String youtubeVideoId
) {
}
