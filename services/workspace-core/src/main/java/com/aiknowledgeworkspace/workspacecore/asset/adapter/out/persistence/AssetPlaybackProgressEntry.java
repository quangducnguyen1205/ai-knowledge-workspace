package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

    AssetPlaybackProgressEntry(
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed,
            Instant updatedAt
    ) {
        this.assetId = assetId;
        this.userId = userId;
        this.positionMs = positionMs;
        this.completed = completed;
        this.updatedAt = updatedAt;
    }

    /** Last write wins: the newest request replaces position, completion and timestamp. */
    void apply(long positionMs, boolean completed, Instant updatedAt) {
        this.positionMs = positionMs;
        this.completed = completed;
        this.updatedAt = updatedAt;
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
