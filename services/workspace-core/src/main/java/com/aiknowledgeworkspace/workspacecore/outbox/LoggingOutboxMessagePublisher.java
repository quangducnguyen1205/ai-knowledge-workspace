package com.aiknowledgeworkspace.workspacecore.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local placeholder publisher for Phase 3B. It performs no external broker delivery.
 */
public class LoggingOutboxMessagePublisher implements OutboxMessagePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxMessagePublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        LOGGER.warn(
                "Outbox relay local placeholder accepted event id={} type={} version={} key={}; no external delivery performed",
                event.getId(),
                event.getEventType(),
                event.getEventVersion(),
                event.getEventKey()
        );
    }
}
