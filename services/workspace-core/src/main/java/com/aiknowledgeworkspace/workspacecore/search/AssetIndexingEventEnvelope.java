package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedPayload;
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
