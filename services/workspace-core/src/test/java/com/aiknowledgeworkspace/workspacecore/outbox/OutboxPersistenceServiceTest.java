package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.persistence.OutboxEventRepository;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.persistence.OutboxPersistenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxPersistenceServiceTest {

    @Test
    void convertsNeutralDraftWithoutChangingEnvelopeIdentityOrPayload() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OutboxDraft draft = new OutboxDraft(
                eventId, "event.type", 3, "Aggregate", aggregateId, "event-key", "{\"value\":null}"
        );

        UUID persistedId = new OutboxPersistenceService(repository).enqueue(draft);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(event.capture());
        assertThat(persistedId).isEqualTo(eventId);
        assertThat(event.getValue().getId()).isEqualTo(eventId);
        assertThat(event.getValue().getEventType()).isEqualTo("event.type");
        assertThat(event.getValue().getEventVersion()).isEqualTo(3);
        assertThat(event.getValue().getAggregateType()).isEqualTo("Aggregate");
        assertThat(event.getValue().getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getValue().getEventKey()).isEqualTo("event-key");
        assertThat(event.getValue().getPayload()).isEqualTo("{\"value\":null}");
        assertThat(event.getValue().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getValue().getAttemptCount()).isZero();
    }
}
