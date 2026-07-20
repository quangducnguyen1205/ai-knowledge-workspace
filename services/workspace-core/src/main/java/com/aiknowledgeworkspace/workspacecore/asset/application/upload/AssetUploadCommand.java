package com.aiknowledgeworkspace.workspacecore.asset.application.upload;

import java.util.UUID;

public record AssetUploadCommand(
        UUID workspaceId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String requestedTitle,
        AssetUploadContent content
) {
}
