package com.aiknowledgeworkspace.workspacecore.search.relay;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
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
    private final OutboxRelayService outboxRelayService;

    public IndexingRequestRelayScheduler(
            IndexingRequestRelayProperties properties,
            WorkspaceKafkaProperties kafkaProperties,
            OutboxRelayService outboxRelayService
    ) {
        this.properties = properties;
        this.kafkaProperties = kafkaProperties;
        this.outboxRelayService = outboxRelayService;
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
        return outboxRelayService.relayDueIndexingRequestEvents(properties.getBatchSize());
    }
}
