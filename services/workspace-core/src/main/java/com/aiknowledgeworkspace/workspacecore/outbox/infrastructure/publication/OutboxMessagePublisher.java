package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;

public interface OutboxMessagePublisher {

    /**
     * Phase 3C publishing boundary. Kafka is the first external delivery implementation,
     * while fallback implementations must be explicit about local-only behavior.
     */
    void publish(OutboxEvent event);
}
