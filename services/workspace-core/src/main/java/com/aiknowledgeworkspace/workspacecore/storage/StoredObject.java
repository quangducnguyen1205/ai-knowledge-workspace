package com.aiknowledgeworkspace.workspacecore.storage;

public record StoredObject(
        String bucket,
        String objectKey,
        long sizeBytes,
        String contentType,
        String eTag
) {
}
