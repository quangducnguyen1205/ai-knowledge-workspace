package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class OutboxPersistenceAdapter implements OutboxWriter, OutboxEventStore {

    private final OutboxEventJpaRepository repository;

    OutboxPersistenceAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UUID enqueue(OutboxDraft draft) {
        return repository.save(OutboxEvent.fromDraft(draft)).getId();
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        return repository.findById(eventId);
    }

    @Override
    public List<OutboxEvent> findByAggregate(String aggregateType, UUID aggregateId) {
        return repository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }

    @Override
    public List<UUID> findDueEventIds(OutboxEventStatus status, Instant now, int limit) {
        return repository.findDueEventIds(status, now, PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> findDueEventIdsByType(
            OutboxEventStatus status,
            String eventType,
            Instant now,
            int limit
    ) {
        return repository.findDueEventIdsByEventType(status, eventType, now, PageRequest.of(0, limit));
    }

    @Override
    public int markPublishing(
            UUID eventId,
            OutboxEventStatus expected,
            OutboxEventStatus updated,
            Instant now
    ) {
        return repository.markPublishing(eventId, expected, updated, now);
    }

    @Override
    public List<UUID> findEligibleRecoveryIds(
            OutboxEventStatus status,
            OutboxFailureDisposition disposition,
            Instant now,
            int maxCycles,
            int limit
    ) {
        return repository.findEligibleRecoveryIds(
                status, disposition, now, maxCycles, PageRequest.of(0, limit)
        );
    }

    @Override
    public int requeueFailedForRecovery(
            UUID eventId,
            OutboxEventStatus failed,
            OutboxEventStatus pending,
            OutboxFailureDisposition disposition,
            Instant now,
            int maxCycles
    ) {
        return repository.requeueFailedForRecovery(eventId, failed, pending, disposition, now, maxCycles);
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return repository.save(event);
    }
}
