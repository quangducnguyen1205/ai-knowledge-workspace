package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(MockitoExtension.class)
class ProcessingRecoveryCommandRunnerTest {

    @Mock
    private ProcessingRecoveryService processingRecoveryService;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @Test
    void noneCommandDoesNothingAndKeepsApplicationRunning() {
        ProcessingRecoveryProperties properties = new ProcessingRecoveryProperties();

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(processingRecoveryService, never()).retryFailedResultEventOnce(org.mockito.ArgumentMatchers.any());
        verify(processingRecoveryService, never()).requeueStuckOutboxEventOnce(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(applicationContext, never()).close();
    }

    @Test
    void retryFailedResultCommandRequiresResultEventIdAndClosesApplication() {
        ProcessingRecoveryProperties properties = new ProcessingRecoveryProperties();
        properties.setCommand(ProcessingRecoveryCommand.RETRY_FAILED_RESULT_EVENT_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("result-event-id is required");

        verify(processingRecoveryService, never()).retryFailedResultEventOnce(org.mockito.ArgumentMatchers.any());
        verify(applicationContext).close();
    }

    @Test
    void retryFailedResultCommandInvokesOnlySelectedEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        ProcessingRecoveryProperties properties = new ProcessingRecoveryProperties();
        properties.setCommand(ProcessingRecoveryCommand.RETRY_FAILED_RESULT_EVENT_ONCE);
        properties.setResultEventId(eventId);
        when(processingRecoveryService.retryFailedResultEventOnce(eventId)).thenReturn(new ProcessingResultHandleResult(
                eventId,
                ConsumedProcessingResultEventStatus.APPLIED,
                true
        ));

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(processingRecoveryService).retryFailedResultEventOnce(eventId);
        verify(processingRecoveryService, never()).requeueStuckOutboxEventOnce(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(applicationContext).close();
    }

    @Test
    void requeueStuckOutboxCommandRequiresOutboxEventIdAndClosesApplication() {
        ProcessingRecoveryProperties properties = new ProcessingRecoveryProperties();
        properties.setCommand(ProcessingRecoveryCommand.REQUEUE_STUCK_OUTBOX_EVENT_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox-event-id is required");

        verify(processingRecoveryService, never()).requeueStuckOutboxEventOnce(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(applicationContext).close();
    }

    @Test
    void requeueStuckOutboxCommandInvokesOnlySelectedEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        Duration minimumAge = Duration.ofMinutes(10);
        ProcessingRecoveryProperties properties = new ProcessingRecoveryProperties();
        properties.setCommand(ProcessingRecoveryCommand.REQUEUE_STUCK_OUTBOX_EVENT_ONCE);
        properties.setOutboxEventId(eventId);
        properties.setMinimumPublishingAge(minimumAge);
        when(processingRecoveryService.requeueStuckOutboxEventOnce(eventId, minimumAge))
                .thenReturn(OutboxEventStatus.PENDING);

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(processingRecoveryService).requeueStuckOutboxEventOnce(eventId, minimumAge);
        verify(processingRecoveryService, never()).retryFailedResultEventOnce(org.mockito.ArgumentMatchers.any());
        verify(applicationContext).close();
    }

    private ProcessingRecoveryCommandRunner newRunner(ProcessingRecoveryProperties properties) {
        return new ProcessingRecoveryCommandRunner(
                properties,
                processingRecoveryService,
                applicationContext
        );
    }
}
