package com.aiknowledgeworkspace.workspacecore.outbox;

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
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false)
    private Integer eventVersion;

    @Column(nullable = false, length = 128)
    private String aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 255)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(nullable = false)
    private Integer attemptCount = 0;

    @Column
    private Instant nextAttemptAt;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String eventType,
            int eventVersion,
            String aggregateType,
            UUID aggregateId,
            String eventKey,
            String payload
    ) {
        this(null, eventType, eventVersion, aggregateType, aggregateId, eventKey, payload);
    }

    public OutboxEvent(
            UUID id,
            String eventType,
            int eventVersion,
            String aggregateType,
            UUID aggregateId,
            String eventKey,
            String payload
    ) {
        this.id = id;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventKey = eventKey;
        this.payload = payload;
    }

    public void markPublished(Instant publishedAt) {
        status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        nextAttemptAt = null;
        lastError = null;
    }

    public void recordPublishFailure(String errorMessage, Instant nextAttemptAt, int maxAttempts) {
        attemptCount = attemptCount == null ? 1 : attemptCount + 1;
        lastError = errorMessage;
        publishedAt = null;

        if (attemptCount >= maxAttempts) {
            status = OutboxEventStatus.FAILED;
            this.nextAttemptAt = null;
            return;
        }

        status = OutboxEventStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void requeueFromPublishing() {
        status = OutboxEventStatus.PENDING;
        nextAttemptAt = null;
        publishedAt = null;
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

    public String getEventType() {
        return eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
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

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
