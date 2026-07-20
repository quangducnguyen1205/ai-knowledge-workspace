package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import com.aiknowledgeworkspace.workspacecore.processing.application.service.ProcessingRecoveryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxManualRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.api.StuckOutboxRecoveryRequest;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.in.ProcessingResultUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultHandleResult;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEventStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingRecoveryServiceTest {

    @Mock
    private ProcessingResultUseCase resultHandler;

    @Mock
    private OutboxManualRecovery outboxRecovery;

    private ProcessingRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new ProcessingRecoveryService(resultHandler, outboxRecovery);
    }

    @Test
    void retriesExactlyTheSelectedDurableResultEvent() {
        UUID eventId = UUID.randomUUID();
        ProcessingResultHandleResult applied = new ProcessingResultHandleResult(
                eventId, ConsumedProcessingResultEventStatus.APPLIED, true
        );
        when(resultHandler.recoverFailedEvent(eventId)).thenReturn(applied);

        assertThat(service.retryFailedResultEventOnce(eventId)).isEqualTo(applied);
        verify(resultHandler).recoverFailedEvent(eventId);
        verifyNoInteractions(outboxRecovery);
    }

    @Test
    void requeuesOnlyASelectedProcessingRequestEvent() {
        UUID eventId = UUID.randomUUID();
        when(outboxRecovery.requeueStuckPublishing(org.mockito.ArgumentMatchers.any()))
                .thenReturn(OutboxDeliveryStatus.PENDING);

        assertThat(service.requeueStuckOutboxEventOnce(eventId, Duration.ofMinutes(5)))
                .isEqualTo(OutboxDeliveryStatus.PENDING);
        verify(outboxRecovery).requeueStuckPublishing(org.mockito.ArgumentMatchers.argThat(request ->
                request.eventId().equals(eventId)
                        && request.minimumPublishingAge().equals(Duration.ofMinutes(5))
                        && request.requiredEventType().equals("asset.processing.requested")
        ));
    }

    @Test
    void invalidMinimumAgeFailsBeforeRecoveryBoundary() {
        assertThatThrownBy(() -> service.requeueStuckOutboxEventOnce(UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.requeueStuckOutboxEventOnce(
                UUID.randomUUID(), Duration.ofSeconds(-1)
        )).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(resultHandler, outboxRecovery);
    }
}
