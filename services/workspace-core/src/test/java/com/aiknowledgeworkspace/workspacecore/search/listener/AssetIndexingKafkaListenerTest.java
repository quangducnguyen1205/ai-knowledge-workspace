package com.aiknowledgeworkspace.workspacecore.search.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingEventHandler;
import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingEventRejectedException;
import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingHandleResult;
import com.aiknowledgeworkspace.workspacecore.search.AssetSearchIndexJobStatus;
import java.lang.reflect.Method;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;

class AssetIndexingKafkaListenerTest {

    private final AssetIndexingEventHandler assetIndexingEventHandler = mock(AssetIndexingEventHandler.class);
    private final AssetIndexingKafkaListener listener = new AssetIndexingKafkaListener(assetIndexingEventHandler);

    @Test
    void listenerIsDisabledByDefaultInConfigurationProperties() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();

        assertThat(properties.isIndexingListenerEnabled()).isFalse();
        assertThat(properties.getIndexingConsumerGroup()).isEqualTo("workspace-search-indexer-v1");
        assertThat(properties.getIndexingAutoOffsetReset()).isEqualTo("latest");
    }

    @Test
    void listenerBeanRequiresExplicitEnablementProperty() {
        ConditionalOnProperty condition = AssetIndexingKafkaListener.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition.prefix()).isEqualTo("workspace.kafka");
        assertThat(condition.name()).containsExactly("indexing-listener-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void indexedHandlerOutcomeAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent)).thenReturn(result(AssetSearchIndexJobStatus.INDEXED));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void supersededNoOpAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent))
                .thenReturn(result(AssetSearchIndexJobStatus.SUPERSEDED));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void durableFailedHandlerOutcomeAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent)).thenReturn(result(AssetSearchIndexJobStatus.FAILED));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void knownMalformedOrUnsupportedRejectionAcknowledgesExactlyOnce() {
        String rawEvent = "not-json";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent))
                .thenThrow(new AssetIndexingEventRejectedException("Indexing event was not valid JSON"));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void unexpectedExceptionDoesNotAcknowledgeAndIsRethrown() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent))
                .thenThrow(new IllegalStateException("elasticsearch unavailable"));

        assertThatThrownBy(() -> listener.onMessage(record(rawEvent), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("elasticsearch unavailable");

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(0)).acknowledge();
    }

    @Test
    void unexpectedHandlerStatusDoesNotAcknowledgeAndFailsClearly() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(assetIndexingEventHandler.handle(rawEvent)).thenReturn(result(AssetSearchIndexJobStatus.PENDING));

        assertThatThrownBy(() -> listener.onMessage(record(rawEvent), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected asset indexing job status");

        verify(assetIndexingEventHandler).handle(rawEvent);
        verify(acknowledgment, times(0)).acknowledge();
    }

    @Test
    void listenerUsesConfiguredTopicGroupAndContainerFactory() throws NoSuchMethodException {
        Method onMessage = AssetIndexingKafkaListener.class.getDeclaredMethod(
                "onMessage",
                ConsumerRecord.class,
                Acknowledgment.class
        );

        KafkaListener annotation = onMessage.getAnnotation(KafkaListener.class);

        assertThat(annotation.topics()).containsExactly("${workspace.kafka.indexing-requested-topic}");
        assertThat(annotation.groupId()).isEqualTo("${workspace.kafka.indexing-consumer-group}");
        assertThat(annotation.containerFactory())
                .isEqualTo(AssetIndexingKafkaListenerConfiguration.CONTAINER_FACTORY_BEAN_NAME);
        assertThat(annotation.autoStartup()).isEqualTo("${workspace.kafka.indexing-listener-enabled:false}");
    }

    @Test
    void listenerContainerUsesManualImmediateAcknowledgement() {
        AssetIndexingKafkaListenerConfiguration configuration = new AssetIndexingKafkaListenerConfiguration();
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        assertThat(configuration
                .assetIndexingKafkaListenerContainerFactory(consumerFactory, new WorkspaceKafkaProperties())
                .getContainerProperties()
                .getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    }

    @Test
    void consumerPropertiesDisableAutoCommitAndUseConfiguredOffsets() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setBootstrapServers("localhost:9092");
        properties.setIndexingConsumerGroup("custom-search-indexer-group");
        properties.setIndexingAutoOffsetReset("earliest");

        assertThat(AssetIndexingKafkaListenerConfiguration.consumerProperties(properties))
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "custom-search-indexer-group")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    private AssetIndexingHandleResult result(AssetSearchIndexJobStatus status) {
        return new AssetIndexingHandleResult(UUID.randomUUID(), UUID.randomUUID(), status, 2);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("asset.indexing.requested.v1", 0, 42L, "asset-1", value);
    }
}
