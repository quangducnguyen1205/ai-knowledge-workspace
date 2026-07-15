package com.aiknowledgeworkspace.workspacecore.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetStatus status;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @JsonIgnore
    @Column(name = "storage_bucket", nullable = false, length = 255)
    private String storageBucket;

    @JsonIgnore
    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @JsonIgnore
    @Column(name = "etag", length = 255)
    private String eTag;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Asset() {
    }

    public Asset(String originalFilename, String title, AssetStatus status, UUID workspaceId) {
        this.originalFilename = originalFilename;
        this.title = title;
        this.status = status;
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId is required");
    }

    public Asset(
            UUID id,
            String originalFilename,
            String title,
            AssetStatus status,
            UUID workspaceId,
            String storageBucket,
            String objectKey,
            String contentType,
            long sizeBytes,
            String eTag
    ) {
        this(originalFilename, title, status, workspaceId);
        this.id = id;
        this.storageBucket = storageBucket;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.eTag = eTag;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId is required");
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getEtag() {
        return eTag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
