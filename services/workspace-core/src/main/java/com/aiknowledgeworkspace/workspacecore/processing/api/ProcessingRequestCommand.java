package com.aiknowledgeworkspace.workspacecore.processing.api;

import java.util.UUID;

public record ProcessingRequestCommand(
        UUID assetId,
        UUID workspaceId,
        String ownerId,
        String storageBucket,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes
) {
}
