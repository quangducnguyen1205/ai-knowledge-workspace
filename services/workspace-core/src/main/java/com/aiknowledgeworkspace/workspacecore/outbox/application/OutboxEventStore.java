package com.aiknowledgeworkspace.workspacecore.outbox.application;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventStore {

    Optional<OutboxEvent> findById(UUID eventId);

    List<OutboxEvent> findByAggregate(String aggregateType, UUID aggregateId);

    List<UUID> findDueEventIds(OutboxEventStatus status, Instant now, int limit);

    List<UUID> findDueEventIdsByType(OutboxEventStatus status, String eventType, Instant now, int limit);

    int markPublishing(UUID eventId, OutboxEventStatus expected, OutboxEventStatus updated, Instant now);

    List<UUID> findEligibleRecoveryIds(
            OutboxEventStatus status,
            OutboxFailureDisposition disposition,
            Instant now,
            int maxCycles,
            int limit
    );

    int requeueFailedForRecovery(
            UUID eventId,
            OutboxEventStatus failed,
            OutboxEventStatus pending,
            OutboxFailureDisposition disposition,
            Instant now,
            int maxCycles
    );

    OutboxEvent save(OutboxEvent event);
}
