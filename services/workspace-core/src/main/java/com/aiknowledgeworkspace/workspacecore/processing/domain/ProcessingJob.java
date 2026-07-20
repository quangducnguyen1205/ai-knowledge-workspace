package com.aiknowledgeworkspace.workspacecore.processing.domain;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingJobStatus processingJobStatus;

    @Column(length = 64)
    private String rawUpstreamTaskState;

    @Column(nullable = false)
    private UUID processingRequestEventId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProcessingJob() {
    }

    public ProcessingJob(
            UUID assetId,
            ProcessingJobStatus processingJobStatus,
            String rawUpstreamTaskState
    ) {
        this.assetId = assetId;
        this.processingJobStatus = processingJobStatus;
        this.rawUpstreamTaskState = rawUpstreamTaskState;
    }

    @PrePersist
    void onCreate() {
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

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public ProcessingJobStatus getProcessingJobStatus() {
        return processingJobStatus;
    }

    public void setProcessingJobStatus(ProcessingJobStatus processingJobStatus) {
        this.processingJobStatus = processingJobStatus;
    }

    public String getRawUpstreamTaskState() {
        return rawUpstreamTaskState;
    }

    public void setRawUpstreamTaskState(String rawUpstreamTaskState) {
        this.rawUpstreamTaskState = rawUpstreamTaskState;
    }

    public UUID getProcessingRequestEventId() {
        return processingRequestEventId;
    }

    public void setProcessingRequestEventId(UUID processingRequestEventId) {
        this.processingRequestEventId = processingRequestEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
