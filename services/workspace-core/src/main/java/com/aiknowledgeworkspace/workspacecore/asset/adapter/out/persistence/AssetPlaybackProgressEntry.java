package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Read mapping for one user's playback progress. Writes never go through this entity: the store
 * uses one atomic {@code INSERT ... ON CONFLICT DO UPDATE} statement so that concurrent first
 * writes cannot collide on the composite primary key.
 */
@Entity
@Table(name = "asset_playback_progress")
@IdClass(AssetPlaybackProgressEntryId.class)
public class AssetPlaybackProgressEntry {

    @Id
    @Column(nullable = false)
    private UUID assetId;

    @Id
    @Column(nullable = false, length = 255)
    private String userId;

    @Column(name = "position_ms", nullable = false)
    private long positionMs;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AssetPlaybackProgressEntry() {
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getUserId() {
        return userId;
    }

    public long getPositionMs() {
        return positionMs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
