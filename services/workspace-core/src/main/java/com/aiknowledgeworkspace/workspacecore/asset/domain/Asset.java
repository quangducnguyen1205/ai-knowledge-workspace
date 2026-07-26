package com.aiknowledgeworkspace.workspacecore.asset.domain;

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

    private static final int MAX_YOUTUBE_VIDEO_ID_LENGTH = 128;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private AssetSourceType sourceType;

    @Column(name = "youtube_video_id", length = MAX_YOUTUBE_VIDEO_ID_LENGTH)
    private String youtubeVideoId;

    @Column(length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetStatus status;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @JsonIgnore
    @Column(name = "storage_bucket", length = 255)
    private String storageBucket;

    @JsonIgnore
    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(length = 255)
    private String contentType;

    @Column
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

    private Asset(
            UUID id,
            AssetSourceType sourceType,
            String youtubeVideoId,
            String originalFilename,
            String title,
            AssetStatus status,
            UUID workspaceId,
            String storageBucket,
            String objectKey,
            String contentType,
            Long sizeBytes,
            String eTag
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType is required");
        this.youtubeVideoId = youtubeVideoId;
        this.originalFilename = originalFilename;
        this.title = requireText(title, "title");
        this.status = Objects.requireNonNull(status, "status is required");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId is required");
        this.storageBucket = storageBucket;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.eTag = eTag;
        validateState();
    }

    public static Asset uploaded(
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
        return new Asset(
                id,
                AssetSourceType.UPLOAD,
                null,
                requireText(originalFilename, "originalFilename"),
                title,
                status,
                workspaceId,
                requireText(storageBucket, "storageBucket"),
                requireText(objectKey, "objectKey"),
                requireText(contentType, "contentType"),
                sizeBytes,
                eTag
        );
    }

    public static Asset youtube(
            UUID id,
            String youtubeVideoId,
            String title,
            AssetStatus status,
            UUID workspaceId
    ) {
        return new Asset(
                id,
                AssetSourceType.YOUTUBE,
                normalizeYoutubeVideoId(youtubeVideoId),
                null,
                title,
                status,
                workspaceId,
                null,
                null,
                null,
                null,
                null
        );
    }

    @PrePersist
    void onCreate() {
        validateState();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        validateState();
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AssetSourceType getSourceType() {
        return sourceType;
    }

    public String getYoutubeVideoId() {
        return youtubeVideoId;
    }

    public String getOriginalFilename() {
        return originalFilename;
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

    private void validateState() {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sourceType, "sourceType is required");
        requireText(title, "title");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(workspaceId, "workspaceId is required");

        switch (sourceType) {
            case UPLOAD -> validateUploadState();
            case YOUTUBE -> validateYoutubeState();
        }
    }

    private void validateUploadState() {
        if (youtubeVideoId != null) {
            throw new IllegalArgumentException("uploaded asset must not have youtubeVideoId");
        }
        requireText(originalFilename, "originalFilename");
        requireText(storageBucket, "storageBucket");
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        if (sizeBytes == null || sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be greater than or equal to 0");
        }
    }

    private void validateYoutubeState() {
        String normalizedVideoId = normalizeYoutubeVideoId(youtubeVideoId);
        if (!normalizedVideoId.equals(youtubeVideoId)) {
            throw new IllegalArgumentException("youtubeVideoId must be normalized");
        }
        if (originalFilename != null
                || storageBucket != null
                || objectKey != null
                || contentType != null
                || sizeBytes != null
                || eTag != null) {
            throw new IllegalArgumentException("youtube asset must not have upload storage fields");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeYoutubeVideoId(String youtubeVideoId) {
        String normalized = requireText(youtubeVideoId, "youtubeVideoId").trim();
        if (normalized.length() > MAX_YOUTUBE_VIDEO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "youtubeVideoId must be less than or equal to " + MAX_YOUTUBE_VIDEO_ID_LENGTH + " characters"
            );
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("youtubeVideoId must not contain whitespace or control characters");
        }
        return normalized;
    }
}
