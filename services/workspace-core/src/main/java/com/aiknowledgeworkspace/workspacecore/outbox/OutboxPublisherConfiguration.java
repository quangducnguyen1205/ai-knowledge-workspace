package com.aiknowledgeworkspace.workspacecore.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OutboxPublisherConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxMessagePublisher.class)
    OutboxMessagePublisher outboxMessagePublisher() {
        return new LoggingOutboxMessagePublisher();
    }
}
