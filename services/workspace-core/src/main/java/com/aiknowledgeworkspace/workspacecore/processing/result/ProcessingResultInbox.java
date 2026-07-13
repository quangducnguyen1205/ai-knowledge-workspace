package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ProcessingResultInbox {

    private final ConsumedProcessingResultEventRepository consumedEventRepository;

    ProcessingResultInbox(ConsumedProcessingResultEventRepository consumedEventRepository) {
        this.consumedEventRepository = consumedEventRepository;
    }

    ConsumedProcessingResultEvent loadOrCreate(ProcessingResultEventEnvelope event) {
        return consumedEventRepository.findById(event.eventId())
                .orElseGet(() -> new ConsumedProcessingResultEvent(
                        event.eventId(),
                        event.eventType(),
                        event.aggregateId(),
                        event.causationEventId(),
                        event.occurredAt()
                ));
    }

    Optional<ProcessingResultHandleResult> completedOrBlockedDuplicate(
            ConsumedProcessingResultEvent consumedEvent,
            boolean manualRecovery
    ) {
        if (consumedEvent.getStatus() == ConsumedProcessingResultEventStatus.APPLIED) {
            return Optional.of(new ProcessingResultHandleResult(
                    consumedEvent.getEventId(), consumedEvent.getStatus(), false
            ));
        }
        if (consumedEvent.getStatus() == ConsumedProcessingResultEventStatus.FAILED && !manualRecovery) {
            return Optional.of(new ProcessingResultHandleResult(
                    consumedEvent.getEventId(), consumedEvent.getStatus(), false
            ));
        }
        return Optional.empty();
    }

    void markReceivedForApplication(ConsumedProcessingResultEvent consumedEvent) {
        consumedEvent.markReceivedForRetry();
        consumedEventRepository.save(consumedEvent);
    }

    void markApplied(ConsumedProcessingResultEvent consumedEvent) {
        consumedEvent.markApplied();
        consumedEventRepository.save(consumedEvent);
    }

    void markFailed(
            ConsumedProcessingResultEvent consumedEvent,
            String safeErrorDetail,
            String recoverableEventJson
    ) {
        consumedEvent.markFailed(safeErrorDetail, recoverableEventJson);
        consumedEventRepository.save(consumedEvent);
    }

    String requireRecoverableFailedEventJson(UUID eventId) {
        ConsumedProcessingResultEvent consumedEvent = consumedEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Consumed processing result event was not found: " + eventId
                ));
        if (consumedEvent.getStatus() != ConsumedProcessingResultEventStatus.FAILED) {
            throw new IllegalStateException(
                    "Consumed processing result event is not FAILED: " + eventId
                            + " status=" + consumedEvent.getStatus()
            );
        }
        if (!StringUtils.hasText(consumedEvent.getRecoverableEventJson())) {
            throw new IllegalStateException(
                    "Consumed processing result event does not have a recoverable event envelope: " + eventId
            );
        }
        return consumedEvent.getRecoverableEventJson();
    }
}
