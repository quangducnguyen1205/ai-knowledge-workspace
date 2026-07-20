package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEvent;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEventStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingResultEventStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingResultInboxTest {

    private final ProcessingResultEventStore repository =
            mock(ProcessingResultEventStore.class);
    private final ProcessingResultInbox inbox = new ProcessingResultInbox(repository);

    @Test
    void createsInboxRecordFromProcessingContractAndPersistsStateThroughIntentNamedOperations() {
        ProcessingResultEventEnvelope event = event();
        when(repository.findEventById(event.eventId())).thenReturn(Optional.empty());

        ConsumedProcessingResultEvent consumedEvent = inbox.loadOrCreate(event);
        inbox.markReceivedForApplication(consumedEvent);
        inbox.markApplied(consumedEvent);

        assertThat(consumedEvent.getEventId()).isEqualTo(event.eventId());
        assertThat(consumedEvent.getAggregateId()).isEqualTo(event.aggregateId());
        assertThat(consumedEvent.getCausationEventId()).isEqualTo(event.causationEventId());
        assertThat(consumedEvent.getStatus()).isEqualTo(ConsumedProcessingResultEventStatus.APPLIED);
        verify(repository, times(2)).save(consumedEvent);
    }

    @Test
    void completedAndDurablyFailedEventsRemainIdempotentUnlessRecoveryIsExplicit() {
        ConsumedProcessingResultEvent applied = consumedEvent();
        applied.markApplied();
        ConsumedProcessingResultEvent failed = consumedEvent();
        failed.markFailed("safe-category", "{}");

        assertThat(inbox.completedOrBlockedDuplicate(applied, false)).isPresent();
        assertThat(inbox.completedOrBlockedDuplicate(failed, false)).isPresent();
        assertThat(inbox.completedOrBlockedDuplicate(failed, true)).isEmpty();
    }

    @Test
    void exactRecoverySelectionRequiresFailedStateAndRetainedEnvelope() {
        UUID eventId = UUID.randomUUID();
        ConsumedProcessingResultEvent failed = consumedEvent(eventId);
        failed.markFailed("safe-category", "{\"eventId\":\"" + eventId + "\"}");
        when(repository.findEventById(eventId)).thenReturn(Optional.of(failed));

        assertThat(inbox.requireRecoverableFailedEventJson(eventId)).contains(eventId.toString());

        ConsumedProcessingResultEvent applied = consumedEvent(eventId);
        applied.markApplied();
        when(repository.findEventById(eventId)).thenReturn(Optional.of(applied));
        assertThatThrownBy(() -> inbox.requireRecoverableFailedEventJson(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not FAILED");
    }

    private ProcessingResultEventEnvelope event() {
        UUID requestEventId = UUID.randomUUID();
        return new ProcessingResultEventEnvelope(
                UUID.randomUUID(),
                ProcessingResultEventParser.TRANSCRIPT_READY,
                1,
                "ASSET",
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                requestEventId,
                Instant.parse("2026-07-13T00:00:00Z"),
                new TranscriptReadyPayload(requestEventId)
        );
    }

    private ConsumedProcessingResultEvent consumedEvent() {
        return consumedEvent(UUID.randomUUID());
    }

    private ConsumedProcessingResultEvent consumedEvent(UUID eventId) {
        return new ConsumedProcessingResultEvent(
                eventId,
                ProcessingResultEventParser.TRANSCRIPT_READY,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-07-13T00:00:00Z")
        );
    }
}
