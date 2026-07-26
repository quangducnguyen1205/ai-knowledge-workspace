package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String originalFilename,
        String title,
        AssetStatus status,
        UUID workspaceId,
        AssetSourceType sourceType,
        String youtubeVideoId,
        String contentType,
        Long sizeBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssetResponse from(AssetView view) {
        return new AssetResponse(
                view.id(),
                view.originalFilename(),
                view.title(),
                view.status(),
                view.workspaceId(),
                view.sourceType(),
                view.youtubeVideoId(),
                view.contentType(),
                view.sizeBytes(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
