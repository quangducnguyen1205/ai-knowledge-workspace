package com.aiknowledgeworkspace.workspacecore.storage.api;

public record StoredObjectMetadata(
        long sizeBytes,
        String contentType
) {
}
