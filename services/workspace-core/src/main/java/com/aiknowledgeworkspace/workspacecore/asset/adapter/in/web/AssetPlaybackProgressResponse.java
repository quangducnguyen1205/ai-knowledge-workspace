package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPlaybackProgressView;
import java.time.Instant;
import java.util.UUID;

public record AssetPlaybackProgressResponse(
        UUID assetId,
        long positionMs,
        boolean completed,
        Instant updatedAt
) {
    public static AssetPlaybackProgressResponse from(AssetPlaybackProgressView view) {
        return new AssetPlaybackProgressResponse(
                view.assetId(),
                view.positionMs(),
                view.completed(),
                view.updatedAt()
        );
    }
}
