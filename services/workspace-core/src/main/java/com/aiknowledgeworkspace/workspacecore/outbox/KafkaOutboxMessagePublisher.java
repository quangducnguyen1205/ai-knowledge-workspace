package com.aiknowledgeworkspace.workspacecore.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher, AutoCloseable {

    private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaSender kafkaSender;
    private final WorkspaceKafkaProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaOutboxMessagePublisher(WorkspaceKafkaProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new SpringKafkaSender(properties));
    }

    KafkaOutboxMessagePublisher(
            WorkspaceKafkaProperties properties,
            ObjectMapper objectMapper,
            KafkaSender kafkaSender
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.kafkaSender = kafkaSender;
    }

    @Override
    public void publish(OutboxEvent event) {
        String envelope = buildEnvelope(event);
        try {
            kafkaSender
                    .send(properties.getProcessingRequestedTopic(), event.getEventKey(), envelope)
                    .get(resolvedSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Kafka publish was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException("Kafka publish failed", exception);
        }
    }

    @Override
    public void close() {
        if (kafkaSender instanceof AutoCloseable closeableSender) {
            try {
                closeableSender.close();
            } catch (Exception exception) {
                throw new OutboxPublishException("Kafka publisher cleanup failed", exception);
            }
        }
    }

    String buildEnvelope(OutboxEvent event) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", requireId(event.getId()));
        envelope.put("eventType", event.getEventType());
        envelope.put("eventVersion", event.getEventVersion());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("aggregateId", requireId(event.getAggregateId()));
        envelope.put("occurredAt", resolveOccurredAt(event).toString());
        envelope.set("payload", readPayload(event));

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new OutboxPublishException("Kafka envelope serialization failed", exception);
        }
    }

    private JsonNode readPayload(OutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException exception) {
            throw new OutboxPublishException("Outbox event payload is not valid JSON", exception);
        }
    }

    private Instant resolveOccurredAt(OutboxEvent event) {
        return event.getCreatedAt() == null ? Instant.EPOCH : event.getCreatedAt();
    }

    private String requireId(UUID id) {
        if (id == null) {
            throw new OutboxPublishException("Outbox event cannot be published without an id");
        }
        return id.toString();
    }

    private Duration resolvedSendTimeout() {
        Duration sendTimeout = properties.getSendTimeout();
        if (sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()) {
            return DEFAULT_SEND_TIMEOUT;
        }
        return sendTimeout;
    }

    static Map<String, Object> producerProperties(WorkspaceKafkaProperties properties) {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        producerProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, resolvedSendTimeoutMillis(properties));
        producerProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, resolvedRequestTimeoutMillis(properties));
        return producerProperties;
    }

    private static int resolvedSendTimeoutMillis(WorkspaceKafkaProperties properties) {
        long timeoutMillis = properties.getSendTimeout() == null ? 10_000 : properties.getSendTimeout().toMillis();
        return (int) Math.max(1_000, Math.min(timeoutMillis, Integer.MAX_VALUE));
    }

    private static int resolvedRequestTimeoutMillis(WorkspaceKafkaProperties properties) {
        return Math.max(1_000, resolvedSendTimeoutMillis(properties) / 2);
    }

    interface KafkaSender {
        CompletableFuture<?> send(String topic, String key, String value);
    }

    private static final class SpringKafkaSender implements KafkaSender, AutoCloseable {

        private final DefaultKafkaProducerFactory<String, String> producerFactory;
        private final KafkaTemplate<String, String> kafkaTemplate;

        private SpringKafkaSender(WorkspaceKafkaProperties properties) {
            this.producerFactory = new DefaultKafkaProducerFactory<>(producerProperties(properties));
            this.kafkaTemplate = new KafkaTemplate<>(producerFactory);
        }

        @Override
        public CompletableFuture<?> send(String topic, String key, String value) {
            return kafkaTemplate.send(topic, key, value);
        }

        @Override
        public void close() {
            producerFactory.destroy();
        }
    }
}
