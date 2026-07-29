package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite identity of {@link AssetPlaybackProgressEntry}: one row per Asset and user. */
public class AssetPlaybackProgressEntryId implements Serializable {

    private UUID assetId;
    private String userId;

    public AssetPlaybackProgressEntryId() {
    }

    public AssetPlaybackProgressEntryId(UUID assetId, String userId) {
        this.assetId = assetId;
        this.userId = userId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssetPlaybackProgressEntryId candidate)) {
            return false;
        }
        return Objects.equals(assetId, candidate.assetId) && Objects.equals(userId, candidate.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, userId);
    }
}
