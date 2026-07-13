package com.aiknowledgeworkspace.workspacecore.processing.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingTriggerMode;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingRequestRelaySchedulerTest {

    @Mock
    private OutboxRelay outboxRelay;

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
        verifyNoInteractions(outboxRelay);
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
        verifyNoInteractions(outboxRelay);
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
        verifyNoInteractions(outboxRelay);
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
        RelayRequest request = RelayRequest.scheduledForType(ProcessingRequestedEventContract.EVENT_TYPE, 3);
        when(outboxRelay.relay(request)).thenReturn(RelayOutcome.batch(2));

        int processedCount = newScheduler(requestRelayProperties, processingProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isEqualTo(2);
        verify(outboxRelay).relay(request);
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
                outboxRelay
        );
    }
}
