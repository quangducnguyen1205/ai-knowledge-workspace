package com.aiknowledgeworkspace.workspacecore.processing.request;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling.ProcessingRequestRelayScheduler;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling.ProcessingRequestRelayProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
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
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);

        int processedCount = newScheduler(requestRelayProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isZero();
        verifyNoInteractions(outboxRelay);
    }

    @Test
    void kafkaDisabledDoesNotDelegateToOutboxRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        requestRelayProperties.setEnabled(true);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();

        int processedCount = newScheduler(requestRelayProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isZero();
        verifyNoInteractions(outboxRelay);
    }

    @Test
    void enabledKafkaRequestModeDelegatesBoundedBatchToRequestRelay() {
        ProcessingRequestRelayProperties requestRelayProperties = new ProcessingRequestRelayProperties();
        requestRelayProperties.setEnabled(true);
        requestRelayProperties.setBatchSize(3);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);
        RelayRequest request = RelayRequest.scheduledForType(ProcessingRequestedEventContract.EVENT_TYPE, 3);
        when(outboxRelay.relay(request)).thenReturn(RelayOutcome.batch(2));

        int processedCount = newScheduler(requestRelayProperties, kafkaProperties)
                .relayDueRequestsOnce();

        assertThat(processedCount).isEqualTo(2);
        verify(outboxRelay).relay(request);
    }

    private ProcessingRequestRelayScheduler newScheduler(
            ProcessingRequestRelayProperties requestRelayProperties,
            WorkspaceKafkaProperties kafkaProperties
    ) {
        return new ProcessingRequestRelayScheduler(
                requestRelayProperties,
                kafkaProperties,
                outboxRelay
        );
    }
}
