package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "workspace.kafka", name = "enabled", havingValue = "true")
class KafkaPublisherConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxMessagePublisher.class)
    OutboxMessagePublisher kafkaOutboxMessagePublisher(
            WorkspaceKafkaProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<KafkaOutboxMessagePublisher.KafkaSender> kafkaSenderProvider
    ) {
        KafkaOutboxMessagePublisher.KafkaSender kafkaSender = kafkaSenderProvider.getIfAvailable();
        if (kafkaSender == null) {
            return new KafkaOutboxMessagePublisher(properties, objectMapper);
        }
        return new KafkaOutboxMessagePublisher(properties, objectMapper, kafkaSender);
    }
}
