package com.aiknowledgeworkspace.workspacecore.storage;

import java.io.InputStream;

public record StoreObjectRequest(
        String bucket,
        String objectKey,
        InputStream inputStream,
        long sizeBytes,
        String contentType
) {
}
