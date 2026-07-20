package com.aiknowledgeworkspace.workspacecore.outbox.api;

import java.util.Objects;
import java.util.UUID;

public record OutboxDraft(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        String eventKey,
        String payload
) {
    public OutboxDraft {
        Objects.requireNonNull(eventId, "eventId is required");
        requireText(eventType, "eventType");
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        requireText(eventKey, "eventKey");
        requireText(payload, "payload");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
