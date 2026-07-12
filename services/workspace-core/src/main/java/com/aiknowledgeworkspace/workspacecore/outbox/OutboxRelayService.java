package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final OutboxRelayProperties outboxRelayProperties;
    private final OutboxRecoveryProperties outboxRecoveryProperties;
    private final OutboxFailureClassifier failureClassifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            OutboxRecoveryProperties outboxRecoveryProperties,
            OutboxFailureClassifier failureClassifier,
            TransactionTemplate transactionTemplate
    ) {
        this(
                outboxEventRepository,
                outboxMessagePublisher,
                outboxRelayProperties,
                outboxRecoveryProperties,
                failureClassifier,
                transactionTemplate,
                Clock.systemUTC()
        );
    }

    OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            OutboxRecoveryProperties outboxRecoveryProperties,
            OutboxFailureClassifier failureClassifier,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxMessagePublisher = outboxMessagePublisher;
        this.outboxRelayProperties = outboxRelayProperties;
        this.outboxRecoveryProperties = outboxRecoveryProperties;
        this.failureClassifier = failureClassifier;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public int relayDueEvents() {
        if (!outboxRelayProperties.isEnabled()) {
            return 0;
        }

        List<UUID> dueEventIds = outboxEventRepository.findDueEventIds(
                OutboxEventStatus.PENDING,
                Instant.now(clock),
                PageRequest.of(0, resolvedBatchSize())
        );

        return relaySelectedEventIds(dueEventIds, null, null, false);
    }

    public int relayDueProcessingRequestEvents(int batchSize) {
        List<UUID> dueEventIds = outboxEventRepository.findDueEventIdsByEventType(
                OutboxEventStatus.PENDING,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                Instant.now(clock),
                PageRequest.of(0, Math.max(1, batchSize))
        );

        return relaySelectedEventIds(
                dueEventIds,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                "Automatic processing request relay only supports asset.processing.requested events",
                false
        );
    }

    public int relayDueIndexingRequestEvents(int batchSize) {
        List<UUID> dueEventIds = outboxEventRepository.findDueEventIdsByEventType(
                OutboxEventStatus.PENDING,
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                Instant.now(clock),
                PageRequest.of(0, Math.max(1, batchSize))
        );

        return relaySelectedEventIds(
                dueEventIds,
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                "Automatic indexing request relay only supports asset.indexing.requested events",
                false
        );
    }

    public OutboxEventStatus relayEventByIdOnce(UUID eventId) {
        if (!outboxRelayProperties.isEnabled()) {
            throw new IllegalStateException("Outbox relay is disabled");
        }

        return relaySelectedEventById(
                eventId,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                "Manual smoke relay only supports asset.processing.requested events",
                true
        );
    }

    public OutboxEventStatus relayIndexingEventByIdOnce(UUID eventId) {
        if (!outboxRelayProperties.isEnabled()) {
            throw new IllegalStateException("Outbox relay is disabled");
        }

        return relaySelectedEventById(
                eventId,
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                "Manual search smoke relay only supports asset.indexing.requested events",
                true
        );
    }

    private int relaySelectedEventIds(
            List<UUID> eventIds,
            @Nullable String requiredEventType,
            @Nullable String eventTypeError,
            boolean throwOnInvalidCandidate
    ) {
        int processedCount = 0;
        for (UUID eventId : eventIds) {
            OutboxEventStatus status = relaySelectedEventById(
                    eventId,
                    requiredEventType,
                    eventTypeError,
                    throwOnInvalidCandidate
            );
            if (status != null) {
                processedCount++;
            }
        }

        return processedCount;
    }

    private OutboxEventStatus relaySelectedEventById(
            UUID eventId,
            @Nullable String requiredEventType,
            @Nullable String eventTypeError,
            boolean throwOnInvalidCandidate
    ) {
        OutboxEvent claimedEvent;
        try {
            claimedEvent = claimEventForPublishing(eventId, requiredEventType, eventTypeError);
        } catch (IllegalStateException exception) {
            if (throwOnInvalidCandidate) {
                throw exception;
            }
            return null;
        }

        if (claimedEvent == null) {
            if (throwOnInvalidCandidate) {
                throw new IllegalStateException("Outbox event could not be claimed for publishing: " + eventId);
            }
            return null;
        }

        try {
            outboxMessagePublisher.publish(claimedEvent);
            return markPublished(claimedEvent.getId());
        } catch (RuntimeException exception) {
            return recordPublishFailure(claimedEvent.getId(), exception);
        }
    }

    private OutboxEvent claimEventForPublishing(
            UUID eventId,
            @Nullable String requiredEventType,
            @Nullable String eventTypeError
    ) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
            validateSelectedEvent(event, Instant.now(clock), requiredEventType, eventTypeError);

            int claimedCount = outboxEventRepository.markPublishing(
                    event.getId(),
                    OutboxEventStatus.PENDING,
                    OutboxEventStatus.PUBLISHING,
                    Instant.now(clock)
            );
            if (claimedCount != 1) {
                return null;
            }

            return outboxEventRepository.findById(event.getId()).orElseThrow();
        });
    }

    private OutboxEventStatus markPublished(UUID eventId) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event was not found after publish: " + eventId));
            event.markPublished(Instant.now(clock));
            outboxEventRepository.save(event);
            return event.getStatus();
        });
    }

    private OutboxEventStatus recordPublishFailure(UUID eventId, RuntimeException exception) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event was not found after publish failure: " + eventId));
            Instant failedAt = Instant.now(clock);
            event.recordPublishFailure(
                    failureClassifier.classify(exception),
                    failedAt,
                    failedAt.plus(resolvedRetryDelay()),
                    resolvedMaxAttempts(),
                    outboxRecoveryProperties.getCooldown(),
                    outboxRecoveryProperties.getMaxCycles()
            );
            outboxEventRepository.save(event);
            return event.getStatus();
        });
    }

    private void validateSelectedEvent(
            OutboxEvent event,
            Instant now,
            @Nullable String requiredEventType,
            @Nullable String eventTypeError
    ) {
        if (requiredEventType != null && !requiredEventType.equals(event.getEventType())) {
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

}
