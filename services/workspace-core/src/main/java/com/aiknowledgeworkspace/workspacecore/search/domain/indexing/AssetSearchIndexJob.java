package com.aiknowledgeworkspace.workspacecore.search.domain.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_search_index_jobs")
public class AssetSearchIndexJob {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 128)
    private String snapshotFingerprint;

    @Column(length = 128)
    private String activeFingerprintKey;

    @Column
    private UUID requestOutboxEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetSearchIndexJobStatus status = AssetSearchIndexJobStatus.PENDING;

    @Column(nullable = false)
    private Integer attemptCount = 0;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant indexedAt;

    protected AssetSearchIndexJob() {
    }

    public AssetSearchIndexJob(UUID assetId, String snapshotFingerprint) {
        this(UUID.randomUUID(), assetId, snapshotFingerprint);
    }

    public AssetSearchIndexJob(UUID id, UUID assetId, String snapshotFingerprint) {
        this.id = id;
        this.assetId = assetId;
        this.snapshotFingerprint = snapshotFingerprint;
    }

    public void attachRequestOutboxEvent(UUID requestOutboxEventId) {
        this.requestOutboxEventId = requestOutboxEventId;
    }

    public void markIndexing() {
        status = AssetSearchIndexJobStatus.INDEXING;
        attemptCount = attemptCount == null ? 1 : attemptCount + 1;
        indexedAt = null;
        syncActiveFingerprintKey();
    }

    public void markIndexed(Instant indexedAt) {
        status = AssetSearchIndexJobStatus.INDEXED;
        this.indexedAt = indexedAt;
        lastError = null;
        syncActiveFingerprintKey();
    }

    public void markFailed(String errorDetail) {
        status = AssetSearchIndexJobStatus.FAILED;
        lastError = errorDetail;
        indexedAt = null;
        syncActiveFingerprintKey();
    }

    public void recordLastError(String errorDetail) {
        lastError = errorDetail;
    }

    public void markSuperseded() {
        status = AssetSearchIndexJobStatus.SUPERSEDED;
        indexedAt = null;
        syncActiveFingerprintKey();
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        syncActiveFingerprintKey();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        syncActiveFingerprintKey();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getSnapshotFingerprint() {
        return snapshotFingerprint;
    }

    public String getActiveFingerprintKey() {
        return activeFingerprintKey;
    }

    public UUID getRequestOutboxEventId() {
        return requestOutboxEventId;
    }

    public AssetSearchIndexJobStatus getStatus() {
        return status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    private void syncActiveFingerprintKey() {
        if (status == AssetSearchIndexJobStatus.PENDING || status == AssetSearchIndexJobStatus.INDEXING) {
            activeFingerprintKey = snapshotFingerprint;
            return;
        }
        activeFingerprintKey = null;
    }
}
