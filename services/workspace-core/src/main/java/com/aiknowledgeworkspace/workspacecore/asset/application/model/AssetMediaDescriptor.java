package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.util.Objects;
import java.util.UUID;

public record AssetMediaDescriptor(
        UUID assetId,
        String contentType,
        String originalFilename,
        long totalSizeBytes,
        StoredObjectReference storageReference
) {

    public AssetMediaDescriptor {
        Objects.requireNonNull(assetId, "assetId is required");
        Objects.requireNonNull(storageReference, "storageReference is required");
        if (totalSizeBytes <= 0) {
            throw new IllegalArgumentException("totalSizeBytes must be positive");
        }
    }
}
