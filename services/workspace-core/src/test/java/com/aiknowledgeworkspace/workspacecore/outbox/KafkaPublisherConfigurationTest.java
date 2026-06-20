package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KafkaPublisherConfigurationTest {

    @Test
    void producerPropertiesUseDurableLocalPublisherDefaults() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setBootstrapServers("localhost:9092");
        properties.setSendTimeout(Duration.ofSeconds(10));

        Map<String, Object> producerProperties = KafkaOutboxMessagePublisher.producerProperties(properties);

        assertThat(producerProperties)
                .containsEntry("bootstrap.servers", "localhost:9092")
                .containsEntry("acks", "all")
                .containsEntry("enable.idempotence", true)
                .containsEntry("delivery.timeout.ms", 10_000)
                .containsEntry("request.timeout.ms", 5_000);
        assertThat(((Class<?>) producerProperties.get("key.serializer")).getName())
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(((Class<?>) producerProperties.get("value.serializer")).getName())
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    }
}
