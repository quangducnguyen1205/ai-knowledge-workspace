package com.aiknowledgeworkspace.workspacecore.processing.application;

import java.util.UUID;

public record KafkaProcessingRequestCommand(
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
