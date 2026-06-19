package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Instant;
import java.util.UUID;

public record AssetProcessingRequestedPayload(
        UUID assetId,
        UUID workspaceId,
        String ownerId,
        String storageBucket,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant requestedAt
) {
}
