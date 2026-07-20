package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedPayload;
import java.time.Instant;
import java.util.UUID;

public record AssetIndexingEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        String eventKey,
        Instant occurredAt,
        IndexingRequestedPayload payload
) {
}
