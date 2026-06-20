package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxPublisherConfigurationTest {

    private final OutboxPublisherConfiguration configuration = new OutboxPublisherConfiguration();

    @Test
    void defaultFallbackFailsClearlyInsteadOfClaimingExternalDelivery() {
        OutboxMessagePublisher publisher = configuration.outboxMessagePublisher(new WorkspaceKafkaProperties());

        assertThat(publisher).isInstanceOf(FailingOutboxMessagePublisher.class);
        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("workspace.kafka.enabled=true");
    }

    @Test
    void loggingPlaceholderRequiresExplicitOptIn() {
        WorkspaceKafkaProperties properties = new WorkspaceKafkaProperties();
        properties.setLoggingPlaceholderEnabled(true);

        OutboxMessagePublisher publisher = configuration.outboxMessagePublisher(properties);

        assertThat(publisher).isInstanceOf(LoggingOutboxMessagePublisher.class);
    }
}
