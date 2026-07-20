package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling;

import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workspace.processing.request-relay", name = "enabled", havingValue = "true")
public class ProcessingRequestRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingRequestRelayScheduler.class);

    private final ProcessingRequestRelayProperties properties;
    private final WorkspaceKafkaProperties kafkaProperties;
    private final OutboxRelay outboxRelay;

    public ProcessingRequestRelayScheduler(
            ProcessingRequestRelayProperties properties,
            WorkspaceKafkaProperties kafkaProperties,
            OutboxRelay outboxRelay
    ) {
        this.properties = properties;
        this.kafkaProperties = kafkaProperties;
        this.outboxRelay = outboxRelay;
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
        if (!kafkaProperties.isEnabled()) {
            return 0;
        }
        return outboxRelay.relay(RelayRequest.scheduledForType(
                ProcessingRequestedEventContract.EVENT_TYPE,
                properties.getBatchSize()
        )).processedCount();
    }
}
