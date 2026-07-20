package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import java.time.Instant;
import java.util.UUID;

public record AssetView(
        UUID id,
        String originalFilename,
        String title,
        AssetStatus status,
        UUID workspaceId,
        String contentType,
        Long sizeBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssetView from(Asset asset) {
        return new AssetView(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getTitle(),
                asset.getStatus(),
                asset.getWorkspaceId(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
