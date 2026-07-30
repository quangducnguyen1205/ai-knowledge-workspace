package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingItem;
import java.time.Instant;
import java.util.UUID;

public record ContinueWatchingItemResponse(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        String sourceType,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {

    static ContinueWatchingItemResponse from(ContinueWatchingItem item) {
        return new ContinueWatchingItemResponse(
                item.assetId(),
                item.workspaceId(),
                item.assetTitle(),
                item.sourceType(),
                item.positionMs(),
                item.completed(),
                item.updatedAt()
        );
    }
}
