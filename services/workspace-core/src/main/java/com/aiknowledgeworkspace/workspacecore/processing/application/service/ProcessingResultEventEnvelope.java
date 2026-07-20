package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import java.time.Instant;
import java.util.UUID;

record ProcessingResultEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        String eventKey,
        UUID causationEventId,
        Instant occurredAt,
        ProcessingResultPayload payload
) {
}
