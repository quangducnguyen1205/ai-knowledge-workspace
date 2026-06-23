package com.aiknowledgeworkspace.workspacecore.processing.request;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingTriggerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workspace.processing.request-relay", name = "enabled", havingValue = "true")
public class ProcessingRequestRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingRequestRelayScheduler.class);

    private final ProcessingRequestRelayProperties properties;
    private final ProcessingProperties processingProperties;
    private final WorkspaceKafkaProperties kafkaProperties;
    private final OutboxRelayService outboxRelayService;

    public ProcessingRequestRelayScheduler(
            ProcessingRequestRelayProperties properties,
            ProcessingProperties processingProperties,
            WorkspaceKafkaProperties kafkaProperties,
            OutboxRelayService outboxRelayService
    ) {
        this.properties = properties;
        this.processingProperties = processingProperties;
        this.kafkaProperties = kafkaProperties;
        this.outboxRelayService = outboxRelayService;
    }

    public void relayDueRequestsOnSchedule() {
        int processedCount = relayDueRequestsOnce();
        if (processedCount > 0) {
            LOGGER.info("Relayed {} due asset.processing.requested outbox event(s)", processedCount);
        }
    }

    public int relayDueRequestsOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }
        if (processingProperties.getTriggerMode() != ProcessingTriggerMode.KAFKA_REQUEST) {
            return 0;
        }
        if (!kafkaProperties.isEnabled()) {
            return 0;
        }
        return outboxRelayService.relayDueProcessingRequestEvents(properties.getBatchSize());
    }
}
