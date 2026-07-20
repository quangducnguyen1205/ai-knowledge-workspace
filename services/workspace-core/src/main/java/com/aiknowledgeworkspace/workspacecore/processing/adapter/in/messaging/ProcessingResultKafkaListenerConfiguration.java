package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.messaging;

import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
class ProcessingResultKafkaListenerConfiguration {

    static final String CONTAINER_FACTORY_BEAN_NAME = "processingResultKafkaListenerContainerFactory";

    @Bean
    ConsumerFactory<String, String> processingResultKafkaConsumerFactory(WorkspaceKafkaProperties properties) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(properties));
    }

    @Bean(name = CONTAINER_FACTORY_BEAN_NAME)
    ConcurrentKafkaListenerContainerFactory<String, String> processingResultKafkaListenerContainerFactory(
            @Qualifier("processingResultKafkaConsumerFactory") ConsumerFactory<String, String> consumerFactory,
            WorkspaceKafkaProperties properties
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setAutoStartup(properties.isProcessingResultListenerEnabled());
        return factory;
    }

    static Map<String, Object> consumerProperties(WorkspaceKafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getProcessingResultConsumerGroup());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getProcessingResultAutoOffsetReset());
        return consumerProperties;
    }
}
