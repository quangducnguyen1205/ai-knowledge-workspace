package com.aiknowledgeworkspace.workspacecore.search.relay;

import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workspace.search.indexing-relay", name = "enabled", havingValue = "true")
public class IndexingRequestRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexingRequestRelayScheduler.class);

    private final IndexingRequestRelayProperties properties;
    private final WorkspaceKafkaProperties kafkaProperties;
    private final OutboxRelay outboxRelay;

    public IndexingRequestRelayScheduler(
            IndexingRequestRelayProperties properties,
            WorkspaceKafkaProperties kafkaProperties,
            OutboxRelay outboxRelay
    ) {
        this.properties = properties;
        this.kafkaProperties = kafkaProperties;
        this.outboxRelay = outboxRelay;
    }

    public void relayDueIndexingRequestsOnSchedule() {
        int processedCount = relayDueIndexingRequestsOnce();
        if (processedCount > 0) {
            LOGGER.info("Relayed {} due asset.indexing.requested outbox event(s)", processedCount);
        }
    }

    public int relayDueIndexingRequestsOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }
        if (!kafkaProperties.isEnabled()) {
            return 0;
        }
        return outboxRelay.relay(RelayRequest.scheduledForType(
                IndexingRequestedEventContract.EVENT_TYPE,
                properties.getBatchSize()
        )).processedCount();
    }
}
