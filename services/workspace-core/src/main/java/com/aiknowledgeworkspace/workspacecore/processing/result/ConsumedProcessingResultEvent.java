package com.aiknowledgeworkspace.workspacecore.processing.result;

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
@Table(name = "consumed_processing_result_events")
public class ConsumedProcessingResultEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private UUID causationEventId;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConsumedProcessingResultEventStatus status;

    @Column(length = 1024)
    private String errorDetail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ConsumedProcessingResultEvent() {
    }

    public ConsumedProcessingResultEvent(
            UUID eventId,
            String eventType,
            UUID aggregateId,
            UUID causationEventId,
            Instant receivedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.causationEventId = causationEventId;
        this.receivedAt = receivedAt;
        this.status = ConsumedProcessingResultEventStatus.RECEIVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (receivedAt == null) {
            receivedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getCausationEventId() {
        return causationEventId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public ConsumedProcessingResultEventStatus getStatus() {
        return status;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markReceivedForRetry() {
        status = ConsumedProcessingResultEventStatus.RECEIVED;
        processedAt = null;
        errorDetail = null;
    }

    public void markApplied() {
        status = ConsumedProcessingResultEventStatus.APPLIED;
        processedAt = Instant.now();
        errorDetail = null;
    }

    public void markFailed(String errorDetail) {
        status = ConsumedProcessingResultEventStatus.FAILED;
        processedAt = null;
        this.errorDetail = errorDetail;
    }
}
