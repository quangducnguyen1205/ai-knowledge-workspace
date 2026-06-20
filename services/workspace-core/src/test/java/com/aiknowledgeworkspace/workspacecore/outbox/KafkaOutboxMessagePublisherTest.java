package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaOutboxMessagePublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesEnvelopeToConfiguredTopicWithEventKey() throws Exception {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = mock(KafkaOutboxMessagePublisher.KafkaSender.class);
        when(kafkaSender.send(eq("asset.processing.requested.v1"), eq("asset-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(
                kafkaProperties(),
                objectMapper,
                kafkaSender
        );
        OutboxEvent event = newKafkaEvent();

        publisher.publish(event);

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaSender).send(eq("asset.processing.requested.v1"), eq("asset-key"), envelopeCaptor.capture());

        JsonNode envelope = objectMapper.readTree(envelopeCaptor.getValue());
        assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
        assertThat(envelope.path("eventType").asText()).isEqualTo(OutboxEventFactory.ASSET_PROCESSING_REQUESTED);
        assertThat(envelope.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("aggregateType").asText()).isEqualTo(OutboxEventFactory.ASSET_AGGREGATE_TYPE);
        assertThat(envelope.path("aggregateId").asText()).isEqualTo(event.getAggregateId().toString());
        assertThat(envelope.path("occurredAt").asText()).isEqualTo(event.getCreatedAt().toString());
        assertThat(envelope.path("payload").path("assetId").asText()).isEqualTo(event.getAggregateId().toString());
        assertThat(envelope.path("payload").path("objectKey").asText())
                .isEqualTo("users/user-1/workspaces/workspace-1/assets/asset-1/raw/lecture.mp4");
        assertThat(envelope.toString()).doesNotContain("rawBytes", "secret", "password");
    }

    @Test
    void failedKafkaSendThrowsOutboxPublishException() {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = mock(KafkaOutboxMessagePublisher.KafkaSender.class);
        when(kafkaSender.send(eq("asset.processing.requested.v1"), eq("asset-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(
                kafkaProperties(),
                objectMapper,
                kafkaSender
        );

        assertThatThrownBy(() -> publisher.publish(newKafkaEvent()))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessage("Kafka publish failed");
    }

    private WorkspaceKafkaProperties kafkaProperties() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setEnabled(true);
        properties.setProcessingRequestedTopic("asset.processing.requested.v1");
        properties.setSendTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private OutboxEvent newKafkaEvent() {
        UUID assetId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED,
                OutboxEventFactory.ASSET_PROCESSING_REQUESTED_VERSION,
                OutboxEventFactory.ASSET_AGGREGATE_TYPE,
                assetId,
                "asset-key",
                """
                        {
                          "assetId": "%s",
                          "workspaceId": "workspace-1",
                          "storageBucket": "workspace-media",
                          "objectKey": "users/user-1/workspaces/workspace-1/assets/asset-1/raw/lecture.mp4",
                          "contentType": "video/mp4",
                          "sizeBytes": 12345
                        }
                        """.formatted(assetId)
        );
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-06-20T00:00:00Z"));
        ReflectionTestUtils.setField(event, "updatedAt", Instant.parse("2026-06-20T00:00:00Z"));
        return event;
    }
}
