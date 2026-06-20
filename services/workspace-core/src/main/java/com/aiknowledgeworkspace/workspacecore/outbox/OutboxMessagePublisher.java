package com.aiknowledgeworkspace.workspacecore.outbox;

public interface OutboxMessagePublisher {

    /**
     * Phase 3C publishing boundary. Kafka is the first external delivery implementation,
     * while fallback implementations must be explicit about local-only behavior.
     */
    void publish(OutboxEvent event);
}
