package com.aiknowledgeworkspace.workspacecore.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OutboxPublisherConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "workspace.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
    OutboxMessagePublisher outboxMessagePublisher(WorkspaceKafkaProperties properties) {
        if (properties.isLoggingPlaceholderEnabled()) {
            return new LoggingOutboxMessagePublisher();
        }
        return new FailingOutboxMessagePublisher();
    }
}
