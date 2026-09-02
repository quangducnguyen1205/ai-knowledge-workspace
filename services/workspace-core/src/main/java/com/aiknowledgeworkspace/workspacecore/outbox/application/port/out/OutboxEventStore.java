package com.aiknowledgeworkspace.workspacecore.outbox.application.port.out;

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

    /**
     * Ids of events whose publication claim was taken at or before {@code cutoff}, oldest first.
     * The claim instant is the row's {@code updatedAt}, stamped when the relay took it.
     */
    List<UUID> findStalePublishingIds(OutboxEventStatus publishing, Instant cutoff, int limit);

    /**
     * Returns one stale claim to the relay queue, conditional on it still being claimed and still
     * being stale. Returns the number of rows changed, so a caller that loses the race to another
     * instance — or to the relay finishing the send — sees {@code 0} rather than overwriting it.
     */
    int requeueStalePublishing(
            UUID eventId,
            OutboxEventStatus publishing,
            OutboxEventStatus pending,
            Instant cutoff,
            String recoveryCategory,
            Instant now
    );

    int requeueFailedForRecovery(
            UUID eventId,
            OutboxEventStatus failed,
            OutboxEventStatus pending,
            OutboxFailureDisposition disposition,
            Instant now,
            int maxCycles
    );

    /**
     * Counts publication pressure by status in one pass. An event claimed for publication at or
     * before {@code stuckBefore} is reported as stuck.
     */
    OutboxBacklogSnapshot loadBacklogSnapshot(Instant stuckBefore);

    OutboxEvent save(OutboxEvent event);
}
