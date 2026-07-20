package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.messaging;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.messaging.ProcessingResultKafkaListenerConfiguration;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.messaging.ProcessingResultKafkaListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.in.ProcessingResultUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultEventRejectedException;
import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultHandleResult;
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

class ProcessingResultKafkaListenerTest {

    private final ProcessingResultUseCase handler = mock(ProcessingResultUseCase.class);
    private final ProcessingResultKafkaListener listener = new ProcessingResultKafkaListener(handler);

    @Test
    void listenerIsDisabledByDefaultInConfigurationProperties() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();

        assertThat(properties.isProcessingResultListenerEnabled()).isFalse();
        assertThat(properties.getProcessingResultConsumerGroup()).isEqualTo("workspace-processing-result-v1");
        assertThat(properties.getProcessingResultAutoOffsetReset()).isEqualTo("latest");
    }

    @Test
    void listenerBeanRequiresExplicitEnablementProperty() {
        ConditionalOnProperty condition = ProcessingResultKafkaListener.class.getAnnotation(
                ConditionalOnProperty.class
        );

        assertThat(condition.prefix()).isEqualTo("workspace.kafka");
        assertThat(condition.name()).containsExactly("processing-result-listener-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void appliedHandlerOutcomeAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        UUID eventId = UUID.randomUUID();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenReturn(new ProcessingResultHandleResult(
                eventId,
                ConsumedProcessingResultEventStatus.APPLIED,
                true
        ));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void duplicateAlreadyAppliedOutcomeAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenReturn(new ProcessingResultHandleResult(
                UUID.randomUUID(),
                ConsumedProcessingResultEventStatus.APPLIED,
                false
        ));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void durableFailedHandlerOutcomeAcknowledgesExactlyOnce() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenReturn(new ProcessingResultHandleResult(
                UUID.randomUUID(),
                ConsumedProcessingResultEventStatus.FAILED,
                false
        ));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void knownMalformedOrUnsupportedRejectionAcknowledgesExactlyOnce() {
        String rawEvent = "not-json";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenThrow(new ProcessingResultEventRejectedException(
                "Processing result event was not valid JSON"
        ));

        listener.onMessage(record(rawEvent), acknowledgment);

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void unexpectedExceptionDoesNotAcknowledgeAndIsRethrown() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> listener.onMessage(record(rawEvent), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(0)).acknowledge();
    }

    @Test
    void unexpectedHandlerStatusDoesNotAcknowledgeAndFailsClearly() {
        String rawEvent = "{\"eventId\":\"event-1\"}";
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(handler.handle(rawEvent)).thenReturn(new ProcessingResultHandleResult(
                UUID.randomUUID(),
                ConsumedProcessingResultEventStatus.RECEIVED,
                false
        ));

        assertThatThrownBy(() -> listener.onMessage(record(rawEvent), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected processing result handler status");

        verify(handler).handle(rawEvent);
        verify(acknowledgment, times(0)).acknowledge();
    }

    @Test
    void listenerUsesConfiguredTopicGroupAndContainerFactory() throws NoSuchMethodException {
        Method onMessage = ProcessingResultKafkaListener.class.getDeclaredMethod(
                "onMessage",
                ConsumerRecord.class,
                Acknowledgment.class
        );

        KafkaListener annotation = onMessage.getAnnotation(KafkaListener.class);

        assertThat(annotation.topics()).containsExactly("${workspace.kafka.processing-result-topic}");
        assertThat(annotation.groupId()).isEqualTo("${workspace.kafka.processing-result-consumer-group}");
        assertThat(annotation.containerFactory())
                .isEqualTo(ProcessingResultKafkaListenerConfiguration.CONTAINER_FACTORY_BEAN_NAME);
        assertThat(annotation.autoStartup())
                .isEqualTo("${workspace.kafka.processing-result-listener-enabled:false}");
    }

    @Test
    void listenerContainerUsesManualImmediateAcknowledgement() {
        ProcessingResultKafkaListenerConfiguration configuration = new ProcessingResultKafkaListenerConfiguration();
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        assertThat(configuration
                .processingResultKafkaListenerContainerFactory(consumerFactory, new WorkspaceKafkaProperties())
                .getContainerProperties()
                .getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    }

    @Test
    void consumerPropertiesDisableAutoCommitAndUseConfiguredOffsets() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setBootstrapServers("localhost:9092");
        properties.setProcessingResultConsumerGroup("custom-processing-result-group");
        properties.setProcessingResultAutoOffsetReset("earliest");

        assertThat(ProcessingResultKafkaListenerConfiguration.consumerProperties(properties))
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "custom-processing-result-group")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("asset.processing.result.v1", 0, 42L, "asset-1", value);
    }
}
