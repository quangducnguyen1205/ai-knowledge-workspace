package com.aiknowledgeworkspace.workspacecore.outbox;

public interface OutboxMessagePublisher {

    /**
     * Phase 3B publishing boundary. Implementations may acknowledge local relay handling,
     * but Kafka or any other external delivery is intentionally not implemented yet.
     */
    void publish(OutboxEvent event);
}
