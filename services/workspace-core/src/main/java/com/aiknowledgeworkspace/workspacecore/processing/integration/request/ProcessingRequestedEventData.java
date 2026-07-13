package com.aiknowledgeworkspace.workspacecore.processing.integration.request;

import java.util.UUID;

public record ProcessingRequestedEventData(
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
