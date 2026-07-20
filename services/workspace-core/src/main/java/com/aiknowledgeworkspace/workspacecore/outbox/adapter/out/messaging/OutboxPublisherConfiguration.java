package com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging;

import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxPublisherConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "workspace.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
    public OutboxMessagePublisher outboxMessagePublisher(WorkspaceKafkaProperties properties) {
        if (properties.isLoggingPlaceholderEnabled()) {
            return new LoggingOutboxMessagePublisher();
        }
        return new FailingOutboxMessagePublisher();
    }
}
