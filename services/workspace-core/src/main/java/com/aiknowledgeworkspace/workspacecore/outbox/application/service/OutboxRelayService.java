package com.aiknowledgeworkspace.workspacecore.outbox.application.service;

import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRelayProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxPublicationFailureClassifier;
import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayExecutionPolicy;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelaySelection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxRelayService implements OutboxRelay {

    private final OutboxEventStore outboxEventStore;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final OutboxRelayProperties outboxRelayProperties;
    private final OutboxRecoveryProperties outboxRecoveryProperties;
    private final OutboxPublicationFailureClassifier failureClassifier;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            OutboxEventStore outboxEventStore,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            OutboxRecoveryProperties outboxRecoveryProperties,
            OutboxPublicationFailureClassifier failureClassifier,
            TransactionTemplate transactionTemplate
    ) {
        this(
                outboxEventStore,
                outboxMessagePublisher,
                outboxRelayProperties,
                outboxRecoveryProperties,
                failureClassifier,
                transactionTemplate,
                Clock.systemUTC()
        );
    }

    OutboxRelayService(
            OutboxEventStore outboxEventStore,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            OutboxRecoveryProperties outboxRecoveryProperties,
            OutboxPublicationFailureClassifier failureClassifier,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.outboxEventStore = outboxEventStore;
        this.outboxMessagePublisher = outboxMessagePublisher;
        this.outboxRelayProperties = outboxRelayProperties;
        this.outboxRecoveryProperties = outboxRecoveryProperties;
        this.failureClassifier = failureClassifier;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public RelayOutcome relay(RelayRequest request) {
        if (request.executionPolicy().requiresGlobalEnablement() && !outboxRelayProperties.isEnabled()) {
            if (request.executionPolicy().failOnIneligibleCandidate()) {
                throw new IllegalStateException("Outbox relay is disabled");
            }
            return RelayOutcome.batch(0);
        }

        if (request.selection() instanceof RelaySelection.ExactEvent exactEvent) {
            OutboxEventStatus status = relaySelectedEventById(
                    exactEvent.eventId(),
                    EventTypeConstraint.required(
                            exactEvent.requiredEventType(),
                            exactEvent.eventTypeMismatchMessage()
                    ),
                    request.executionPolicy()
            );
            return RelayOutcome.single(toDeliveryStatus(status));
        }

        List<UUID> dueEventIds;
        EventTypeConstraint constraint;
        if (request.selection() instanceof RelaySelection.DueByType dueByType) {
            dueEventIds = outboxEventStore.findDueEventIdsByType(
                    OutboxEventStatus.PENDING,
                    dueByType.eventType(),
                    Instant.now(clock),
                    dueByType.batchSize()
            );
            constraint = EventTypeConstraint.required(
                    dueByType.eventType(),
                    "Scheduled relay only supports the selected event type"
            );
        } else if (request.selection() instanceof RelaySelection.AllDue allDue) {
            dueEventIds = outboxEventStore.findDueEventIds(
                    OutboxEventStatus.PENDING,
                    Instant.now(clock),
                    allDue.batchSize()
            );
            constraint = EventTypeConstraint.any();
        } else {
            throw new IllegalArgumentException("Unsupported relay selection " + request.selection().getClass().getName());
        }

        return RelayOutcome.batch(relaySelectedEventIds(dueEventIds, constraint, request.executionPolicy()));
    }

    private int relaySelectedEventIds(
            List<UUID> eventIds,
            EventTypeConstraint constraint,
            RelayExecutionPolicy executionPolicy
    ) {
        int processedCount = 0;
        for (UUID eventId : eventIds) {
            OutboxEventStatus status = relaySelectedEventById(eventId, constraint, executionPolicy);
            if (status != null) {
                processedCount++;
            }
        }
        return processedCount;
    }

    private OutboxEventStatus relaySelectedEventById(
            UUID eventId,
            EventTypeConstraint constraint,
            RelayExecutionPolicy executionPolicy
    ) {
        OutboxEvent claimedEvent;
        try {
            claimedEvent = claimEventForPublishing(eventId, constraint);
        } catch (IllegalStateException exception) {
            if (executionPolicy.failOnIneligibleCandidate()) {
                throw exception;
            }
            return null;
        }

        if (claimedEvent == null) {
            if (executionPolicy.failOnIneligibleCandidate()) {
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

    private OutboxEvent claimEventForPublishing(UUID eventId, EventTypeConstraint constraint) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventStore.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
            validateSelectedEvent(event, Instant.now(clock), constraint);
            int claimedCount = outboxEventStore.markPublishing(
                    event.getId(),
                    OutboxEventStatus.PENDING,
                    OutboxEventStatus.PUBLISHING,
                    Instant.now(clock)
            );
            if (claimedCount != 1) {
                return null;
            }
            return outboxEventStore.findById(event.getId()).orElseThrow();
        });
    }

    private OutboxEventStatus markPublished(UUID eventId) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventStore.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event was not found after publish: " + eventId));
            event.markPublished(Instant.now(clock));
            outboxEventStore.save(event);
            return event.getStatus();
        });
    }

    private OutboxEventStatus recordPublishFailure(UUID eventId, RuntimeException exception) {
        return transactionTemplate.execute(status -> {
            OutboxEvent event = outboxEventStore.findById(eventId)
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
            outboxEventStore.save(event);
            return event.getStatus();
        });
    }

    private void validateSelectedEvent(OutboxEvent event, Instant now, EventTypeConstraint constraint) {
        if (constraint.requiredEventType().isPresent()
                && !constraint.requiredEventType().orElseThrow().equals(event.getEventType())) {
            throw new IllegalStateException(constraint.mismatchMessage().orElseThrow() + ": " + event.getId());
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

    private OutboxDeliveryStatus toDeliveryStatus(OutboxEventStatus status) {
        return OutboxDeliveryStatus.valueOf(status.name());
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

    private record EventTypeConstraint(Optional<String> requiredEventType, Optional<String> mismatchMessage) {
        private static EventTypeConstraint any() {
            return new EventTypeConstraint(Optional.empty(), Optional.empty());
        }

        private static EventTypeConstraint required(String eventType, String mismatchMessage) {
            return new EventTypeConstraint(Optional.of(eventType), Optional.of(mismatchMessage));
        }
    }
}
