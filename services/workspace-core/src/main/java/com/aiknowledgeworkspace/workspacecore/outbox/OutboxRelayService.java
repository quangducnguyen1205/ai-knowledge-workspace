package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final OutboxRelayProperties outboxRelayProperties;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties
    ) {
        this(outboxEventRepository, outboxMessagePublisher, outboxRelayProperties, Clock.systemUTC());
    }

    OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxMessagePublisher = outboxMessagePublisher;
        this.outboxRelayProperties = outboxRelayProperties;
        this.clock = clock;
    }

    @Transactional
    public int relayDueEvents() {
        if (!outboxRelayProperties.isEnabled()) {
            return 0;
        }

        List<OutboxEvent> dueEvents = outboxEventRepository.findDueEvents(
                OutboxEventStatus.PENDING,
                Instant.now(clock),
                PageRequest.of(0, resolvedBatchSize())
        );

        int processedCount = 0;
        for (OutboxEvent event : dueEvents) {
            if (relayEvent(event)) {
                processedCount++;
            }
        }

        return processedCount;
    }

    @Transactional
    public OutboxEventStatus relayEventByIdOnce(UUID eventId) {
        if (!outboxRelayProperties.isEnabled()) {
            throw new IllegalStateException("Outbox relay is disabled");
        }

        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
        validateSelectedEvent(
                event,
                Instant.now(clock),
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                "Manual smoke relay only supports asset.processing.requested events"
        );

        if (!relayEvent(event)) {
            throw new IllegalStateException("Outbox event could not be claimed for publishing: " + eventId);
        }

        return outboxEventRepository.findById(eventId).orElseThrow().getStatus();
    }

    @Transactional
    public OutboxEventStatus relayIndexingEventByIdOnce(UUID eventId) {
        if (!outboxRelayProperties.isEnabled()) {
            throw new IllegalStateException("Outbox relay is disabled");
        }

        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
        validateSelectedEvent(
                event,
                Instant.now(clock),
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                "Manual search smoke relay only supports asset.indexing.requested events"
        );

        if (!relayEvent(event)) {
            throw new IllegalStateException("Outbox event could not be claimed for publishing: " + eventId);
        }

        return outboxEventRepository.findById(eventId).orElseThrow().getStatus();
    }

    private boolean relayEvent(OutboxEvent event) {
        int claimedCount = outboxEventRepository.markPublishing(
                event.getId(),
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PUBLISHING,
                Instant.now(clock)
        );
        if (claimedCount != 1) {
            return false;
        }

        OutboxEvent claimedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();

        try {
            outboxMessagePublisher.publish(claimedEvent);
            claimedEvent.markPublished(Instant.now(clock));
        } catch (RuntimeException exception) {
            claimedEvent.recordPublishFailure(
                    resolveErrorMessage(exception),
                    Instant.now(clock).plus(resolvedRetryDelay()),
                    resolvedMaxAttempts()
            );
        }

        outboxEventRepository.save(claimedEvent);
        return true;
    }

    private void validateSelectedEvent(
            OutboxEvent event,
            Instant now,
            String requiredEventType,
            String eventTypeError
    ) {
        if (!requiredEventType.equals(event.getEventType())) {
            throw new IllegalStateException(eventTypeError + ": " + event.getId());
        }
        if (event.getStatus() == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Outbox event is already published: " + event.getId());
        }
        if (event.getStatus() != OutboxEventStatus.PENDING) {
            throw new IllegalStateException(
                    "Outbox event is not eligible for relay: " + event.getId() + " status=" + event.getStatus()
            );
        }
        if (event.getNextAttemptAt() != null && event.getNextAttemptAt().isAfter(now)) {
            throw new IllegalStateException(
                    "Outbox event is not due for relay until " + event.getNextAttemptAt() + ": " + event.getId()
            );
        }
    }

    private int resolvedBatchSize() {
        return Math.max(1, outboxRelayProperties.getBatchSize());
    }

    private int resolvedMaxAttempts() {
        return Math.max(1, outboxRelayProperties.getMaxAttempts());
    }

    private Duration resolvedRetryDelay() {
        Duration retryDelay = outboxRelayProperties.getRetryDelay();
        if (retryDelay == null || retryDelay.isNegative()) {
            return Duration.ZERO;
        }
        return retryDelay;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
