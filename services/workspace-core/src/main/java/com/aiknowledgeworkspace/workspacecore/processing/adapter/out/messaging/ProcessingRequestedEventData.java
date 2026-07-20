package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.messaging;

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
