package com.aiknowledgeworkspace.workspacecore.processing;

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

    @Column(nullable = false, unique = true)
    private UUID assetId;

    @Column(nullable = false, length = 128)
    private String fastapiTaskId;

    @Column(nullable = false, length = 128)
    private String fastapiVideoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingJobStatus processingJobStatus;

    @Column(length = 64)
    private String rawUpstreamTaskState;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProcessingJob() {
    }

    public ProcessingJob(
            UUID assetId,
            String fastapiTaskId,
            String fastapiVideoId,
            ProcessingJobStatus processingJobStatus,
            String rawUpstreamTaskState
    ) {
        this.assetId = assetId;
        this.fastapiTaskId = fastapiTaskId;
        this.fastapiVideoId = fastapiVideoId;
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

    public String getFastapiTaskId() {
        return fastapiTaskId;
    }

    public void setFastapiTaskId(String fastapiTaskId) {
        this.fastapiTaskId = fastapiTaskId;
    }

    public String getFastapiVideoId() {
        return fastapiVideoId;
    }

    public void setFastapiVideoId(String fastapiVideoId) {
        this.fastapiVideoId = fastapiVideoId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
