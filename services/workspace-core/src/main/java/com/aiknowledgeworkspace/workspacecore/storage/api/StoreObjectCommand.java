package com.aiknowledgeworkspace.workspacecore.storage.api;

import java.io.InputStream;
import java.util.UUID;

public record StoreObjectCommand(
        String userId,
        UUID workspaceId,
        UUID assetId,
        String originalFilename,
        InputStream inputStream,
        long sizeBytes,
        String contentType
) {
}
