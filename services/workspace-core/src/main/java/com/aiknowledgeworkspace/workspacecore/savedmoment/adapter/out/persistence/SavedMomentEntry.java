package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One user's bookmark of one canonical transcript row. */
@Entity
@Table(name = "saved_moments")
class SavedMomentEntry {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 255)
    private String transcriptRowId;

    @Column(nullable = false)
    private Instant savedAt;

    protected SavedMomentEntry() {
    }

    SavedMomentEntry(
            UUID id,
            String userId,
            UUID workspaceId,
            UUID assetId,
            String transcriptRowId,
            Instant savedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.assetId = assetId;
        this.transcriptRowId = transcriptRowId;
        this.savedAt = savedAt;
    }

    UUID getId() {
        return id;
    }

    String getUserId() {
        return userId;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    UUID getAssetId() {
        return assetId;
    }

    String getTranscriptRowId() {
        return transcriptRowId;
    }

    Instant getSavedAt() {
        return savedAt;
    }
}
