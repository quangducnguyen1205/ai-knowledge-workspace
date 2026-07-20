package com.aiknowledgeworkspace.workspacecore.asset.application.command;

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
