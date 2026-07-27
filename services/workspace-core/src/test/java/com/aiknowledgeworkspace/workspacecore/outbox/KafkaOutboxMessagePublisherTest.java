package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.KafkaOutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.OutboxPublishException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging.PermanentOutboxPublishException;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
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
        assertThat(envelope.path("eventType").asText()).isEqualTo(ProcessingRequestedEventContract.EVENT_TYPE);
        assertThat(envelope.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("aggregateType").asText()).isEqualTo(ProcessingRequestedEventContract.AGGREGATE_TYPE);
        assertThat(envelope.path("aggregateId").asText()).isEqualTo(event.getAggregateId().toString());
        assertThat(envelope.path("eventKey").asText()).isEqualTo("asset-key");
        assertThat(envelope.path("occurredAt").asText()).isEqualTo(event.getCreatedAt().toString());
        assertThat(envelope.path("payload").path("assetId").asText()).isEqualTo(event.getAggregateId().toString());
        assertThat(envelope.path("payload").path("objectKey").asText())
                .isEqualTo("users/user-1/workspaces/workspace-1/assets/asset-1/raw/lecture.mp4");
        assertThat(envelope.toString()).doesNotContain("rawBytes", "secret", "password");
    }

    @Test
    void publishesIndexingEventToConfiguredIndexingTopic() throws Exception {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = mock(KafkaOutboxMessagePublisher.KafkaSender.class);
        when(kafkaSender.send(eq("asset.indexing.requested.v1"), eq("asset-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        WorkspaceKafkaProperties properties = kafkaProperties();
        properties.setIndexingRequestedTopic("asset.indexing.requested.v1");
        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(
                properties,
                objectMapper,
                kafkaSender
        );
        UUID assetId = UUID.randomUUID();
        UUID indexingJobId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                IndexingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                "asset-key",
                """
                        {
                          "assetId": "%s",
                          "indexingJobId": "%s",
                          "snapshotFingerprint": "abc123"
                        }
                        """.formatted(assetId, indexingJobId)
        );
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-06-20T00:00:00Z"));

        publisher.publish(event);

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaSender).send(eq("asset.indexing.requested.v1"), eq("asset-key"), envelopeCaptor.capture());
        JsonNode envelope = objectMapper.readTree(envelopeCaptor.getValue());
        assertThat(envelope.path("eventType").asText()).isEqualTo(IndexingRequestedEventContract.EVENT_TYPE);
        assertThat(envelope.path("eventKey").asText()).isEqualTo("asset-key");
        assertThat(envelope.path("payload").path("indexingJobId").asText()).isEqualTo(indexingJobId.toString());
        assertThat(envelope.toString()).doesNotContain("transcript text", "objectKey", "secret", "password");
    }

    @Test
    void publishesStrictV2EnvelopeToItsOwnTopicWithoutTheInternalEventKeyField() throws Exception {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = mock(KafkaOutboxMessagePublisher.KafkaSender.class);
        when(kafkaSender.send(eq("asset.processing.requested.v2"), eq("asset-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        WorkspaceKafkaProperties properties = kafkaProperties();
        properties.setProcessingRequestedV2Topic("asset.processing.requested.v2");
        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(properties, objectMapper, kafkaSender);
        OutboxEvent event = youtubeV2Event();

        publisher.publish(event);

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaSender).send(eq("asset.processing.requested.v2"), eq("asset-key"), envelopeCaptor.capture());
        JsonNode envelope = objectMapper.readTree(envelopeCaptor.getValue());
        assertThat(envelope.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "eventType", "eventVersion", "aggregateType", "aggregateId", "occurredAt", "payload"
        );
        assertThat(envelope.has("eventKey")).isFalse();
        assertThat(envelope.path("eventVersion").asInt()).isEqualTo(2);
        assertThat(envelope.path("aggregateType").asText()).isEqualTo("ASSET");
        assertThat(envelope.path("payload").path("sourceType").asText()).isEqualTo("YOUTUBE");
        assertThat(envelope.path("payload").path("youtubeVideoId").asText()).isEqualTo("abc_DEF-123");
        assertThat(envelope).isEqualTo(objectMapper.readTree("""
                {
                  "eventId": "44444444-4444-4444-4444-444444444444",
                  "eventType": "asset.processing.requested",
                  "eventVersion": 2,
                  "aggregateType": "ASSET",
                  "aggregateId": "11111111-1111-1111-1111-111111111111",
                  "occurredAt": "2026-07-27T00:00:00Z",
                  "payload": {
                    "assetId": "11111111-1111-1111-1111-111111111111",
                    "workspaceId": "22222222-2222-2222-2222-222222222222",
                    "ownerId": "owner-1",
                    "sourceType": "YOUTUBE",
                    "youtubeVideoId": "abc_DEF-123",
                    "requestedAt": "2026-07-27T00:00:00Z"
                  }
                }
                """));
    }

    @Test
    void unsupportedProcessingRequestVersionFailsRatherThanRoutingToV1() {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = mock(KafkaOutboxMessagePublisher.KafkaSender.class);
        KafkaOutboxMessagePublisher publisher = new KafkaOutboxMessagePublisher(
                kafkaProperties(), objectMapper, kafkaSender
        );
        OutboxEvent event = new OutboxEvent(
                ProcessingRequestedEventContract.EVENT_TYPE,
                3,
                "ASSET",
                UUID.randomUUID(),
                "asset-key",
                "{}"
        );
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-07-27T00:00:00Z"));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(PermanentOutboxPublishException.class)
                .hasMessageContaining("version 3");
    }

    @Test
    void v2TopicHasAStableDefaultWithoutChangingTheV1Default() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();

        assertThat(properties.getProcessingRequestedTopic()).isEqualTo("asset.processing.requested.v1");
        assertThat(properties.getProcessingRequestedV2Topic()).isEqualTo("asset.processing.requested.v2");
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
                ProcessingRequestedEventContract.EVENT_TYPE,
                ProcessingRequestedEventContract.EVENT_VERSION,
                ProcessingRequestedEventContract.AGGREGATE_TYPE,
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

    private OutboxEvent youtubeV2Event() {
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OutboxEvent event = new OutboxEvent(
                YouTubeProcessingRequestedEventContract.EVENT_TYPE,
                YouTubeProcessingRequestedEventContract.EVENT_VERSION,
                YouTubeProcessingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                "asset-key",
                """
                        {
                          "assetId": "11111111-1111-1111-1111-111111111111",
                          "workspaceId": "22222222-2222-2222-2222-222222222222",
                          "ownerId": "owner-1",
                          "sourceType": "YOUTUBE",
                          "youtubeVideoId": "abc_DEF-123",
                          "requestedAt": "2026-07-27T00:00:00Z"
                        }
                        """
        );
        ReflectionTestUtils.setField(event, "id", UUID.fromString("44444444-4444-4444-4444-444444444444"));
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-07-27T00:00:00Z"));
        return event;
    }
}
