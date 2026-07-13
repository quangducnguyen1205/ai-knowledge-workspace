package com.aiknowledgeworkspace.workspacecore.search.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexingRequestRelaySchedulerTest {

    @Mock
    private OutboxRelay outboxRelay;

    @Test
    void disabledRelayDoesNotDelegateToOutboxRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isZero();
        verifyNoInteractions(outboxRelay);
    }

    @Test
    void kafkaDisabledDoesNotDelegateToOutboxRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        indexingRelayProperties.setEnabled(true);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isZero();
        verifyNoInteractions(outboxRelay);
    }

    @Test
    void enabledKafkaModeDelegatesBoundedBatchToIndexingRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        indexingRelayProperties.setEnabled(true);
        indexingRelayProperties.setBatchSize(4);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);
        RelayRequest request = RelayRequest.scheduledForType(IndexingRequestedEventContract.EVENT_TYPE, 4);
        when(outboxRelay.relay(request)).thenReturn(RelayOutcome.batch(3));

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isEqualTo(3);
        verify(outboxRelay).relay(request);
    }

    private IndexingRequestRelayScheduler newScheduler(
            IndexingRequestRelayProperties indexingRelayProperties,
            WorkspaceKafkaProperties kafkaProperties
    ) {
        return new IndexingRequestRelayScheduler(
                indexingRelayProperties,
                kafkaProperties,
                outboxRelay
        );
    }
}
