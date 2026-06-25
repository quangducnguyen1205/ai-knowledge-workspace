package com.aiknowledgeworkspace.workspacecore.search.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexingRequestRelaySchedulerTest {

    @Mock
    private OutboxRelayService outboxRelayService;

    @Test
    void disabledRelayDoesNotDelegateToOutboxRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isZero();
        verify(outboxRelayService, never()).relayDueIndexingRequestEvents(anyInt());
    }

    @Test
    void kafkaDisabledDoesNotDelegateToOutboxRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        indexingRelayProperties.setEnabled(true);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isZero();
        verify(outboxRelayService, never()).relayDueIndexingRequestEvents(anyInt());
    }

    @Test
    void enabledKafkaModeDelegatesBoundedBatchToIndexingRelay() {
        IndexingRequestRelayProperties indexingRelayProperties = new IndexingRequestRelayProperties();
        indexingRelayProperties.setEnabled(true);
        indexingRelayProperties.setBatchSize(4);
        WorkspaceKafkaProperties kafkaProperties = new WorkspaceKafkaProperties();
        kafkaProperties.setEnabled(true);
        when(outboxRelayService.relayDueIndexingRequestEvents(4)).thenReturn(3);

        int processedCount = newScheduler(indexingRelayProperties, kafkaProperties)
                .relayDueIndexingRequestsOnce();

        assertThat(processedCount).isEqualTo(3);
        verify(outboxRelayService).relayDueIndexingRequestEvents(4);
    }

    private IndexingRequestRelayScheduler newScheduler(
            IndexingRequestRelayProperties indexingRelayProperties,
            WorkspaceKafkaProperties kafkaProperties
    ) {
        return new IndexingRequestRelayScheduler(
                indexingRelayProperties,
                kafkaProperties,
                outboxRelayService
        );
    }
}
