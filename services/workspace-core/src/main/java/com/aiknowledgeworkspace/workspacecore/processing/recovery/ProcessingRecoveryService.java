package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventRepository;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventHandler;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingRecoveryService {

    private final ProcessingResultEventHandler processingResultEventHandler;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Autowired
    public ProcessingRecoveryService(
            ProcessingResultEventHandler processingResultEventHandler,
            OutboxEventRepository outboxEventRepository
    ) {
        this(processingResultEventHandler, outboxEventRepository, Clock.systemUTC());
    }

    ProcessingRecoveryService(
            ProcessingResultEventHandler processingResultEventHandler,
            OutboxEventRepository outboxEventRepository,
            Clock clock
    ) {
        this.processingResultEventHandler = processingResultEventHandler;
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
    }

    public ProcessingResultHandleResult retryFailedResultEventOnce(UUID eventId) {
        return processingResultEventHandler.recoverFailedEvent(eventId);
    }

    @Transactional
    public OutboxEventStatus requeueStuckOutboxEventOnce(UUID eventId, Duration minimumPublishingAge) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
        validateStuckPublishingEvent(event, resolvedMinimumPublishingAge(minimumPublishingAge), Instant.now(clock));

        event.requeueFromPublishing();
        outboxEventRepository.save(event);
        return event.getStatus();
    }

    private void validateStuckPublishingEvent(OutboxEvent event, Duration minimumPublishingAge, Instant now) {
        if (!OutboxEventFactory.ASSET_PROCESSING_REQUESTED.equals(event.getEventType())) {
            throw new IllegalStateException(
                    "Manual outbox recovery only supports asset.processing.requested events: " + event.getId()
            );
        }
        if (event.getStatus() == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Outbox event is already published: " + event.getId());
        }
        if (event.getStatus() != OutboxEventStatus.PUBLISHING) {
            throw new IllegalStateException(
                    "Outbox event is not stuck in PUBLISHING: " + event.getId() + " status=" + event.getStatus()
            );
        }

        Instant publishingSince = event.getUpdatedAt() == null ? event.getCreatedAt() : event.getUpdatedAt();
        if (publishingSince.plus(minimumPublishingAge).isAfter(now)) {
            throw new IllegalStateException(
                    "Outbox event is not old enough for PUBLISHING recovery: "
                            + event.getId()
                            + " publishingSince="
                            + publishingSince
                            + " minimumAge="
                            + minimumPublishingAge
            );
        }
    }

    private Duration resolvedMinimumPublishingAge(Duration minimumPublishingAge) {
        if (minimumPublishingAge == null) {
            throw new IllegalStateException("workspace.processing.recovery.minimum-publishing-age is required");
        }
        if (minimumPublishingAge.isNegative()) {
            throw new IllegalStateException("workspace.processing.recovery.minimum-publishing-age must not be negative");
        }
        return minimumPublishingAge;
    }
}
