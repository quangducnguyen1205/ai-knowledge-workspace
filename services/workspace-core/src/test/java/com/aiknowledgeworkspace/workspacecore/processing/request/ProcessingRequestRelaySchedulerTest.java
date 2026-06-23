package com.aiknowledgeworkspace.workspacecore.processing.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingTriggerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingRequestRelaySchedulerTest {

    @Mock
    private OutboxRelayService outboxRelayService;

    @Test
    void disabledRelayDoesNotDelegateToOutboxRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        ProcessingProperties processingProperties = new ProcessingProperties();
        processingProperties.setTriggerMode(ProcessingTriggerMode.KAFKA_REQUEST);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);

        int processedCount = newScheduler(requestRelayProperties, processingProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isZero();
        verify(outboxRelayService, never()).relayDueProcessingRequestEvents(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void directUploadModeDoesNotDelegateToOutboxRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        requestRelayProperties.setEnabled(true);
        ProcessingProperties processingProperties = new ProcessingProperties();
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);

        int processedCount = newScheduler(requestRelayProperties, processingProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isZero();
        verify(outboxRelayService, never()).relayDueProcessingRequestEvents(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void kafkaDisabledDoesNotDelegateToOutboxRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        requestRelayProperties.setEnabled(true);
        ProcessingProperties processingProperties = new ProcessingProperties();
        processingProperties.setTriggerMode(ProcessingTriggerMode.KAFKA_REQUEST);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();

        int processedCount = newScheduler(requestRelayProperties, processingProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isZero();
        verify(outboxRelayService, never()).relayDueProcessingRequestEvents(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void enabledKafkaRequestModeDelegatesBoundedBatchToRequestRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        requestRelayProperties.setEnabled(true);
        requestRelayProperties.setBatchSize(3);
        ProcessingProperties processingProperties = new ProcessingProperties();
        processingProperties.setTriggerMode(ProcessingTriggerMode.KAFKA_REQUEST);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);
        when(outboxRelayService.relayDueProcessingRequestEvents(3)).thenReturn(2);

        int processedCount = newScheduler(requestRelayProperties, processingProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isEqualTo(2);
        verify(outboxRelayService).relayDueProcessingRequestEvents(3);
    }

    private ProcessingRequestRelayScheduler newScheduler(
            ProcessingRequestRelayProperties requestRelayProperties,
            ProcessingProperties processingProperties,
            WorkspaceKafkaProperties kafkaProperties
    ) {
        return new ProcessingRequestRelayScheduler(
                requestRelayProperties,
                processingProperties,
                kafkaProperties,
                outboxRelayService
        );
    }
}
