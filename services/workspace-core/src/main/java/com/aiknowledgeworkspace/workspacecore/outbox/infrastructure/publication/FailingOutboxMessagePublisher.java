package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxMessagePublisher;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;

/**
 * Default safety fallback. It prevents manual relay invocation from marking events
 * as published when no real external publisher is configured.
 */
public class FailingOutboxMessagePublisher implements OutboxMessagePublisher {

    @Override
    public void publish(OutboxEvent event) {
        throw new OutboxPublishException(
                "Outbox relay publisher is not configured. Enable workspace.kafka.enabled=true for Kafka publishing."
        );
    }
}
