package com.aiknowledgeworkspace.workspacecore.outbox.application.service;

import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxRecoveryOrigin;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxFailureRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRecoveryResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxRecoveryService implements OutboxFailureRecovery {

    private final OutboxEventStore repository;
    private final OutboxRecoveryProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public OutboxRecoveryService(
            OutboxEventStore repository,
            OutboxRecoveryProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this(repository, properties, transactionTemplate, Clock.systemUTC());
    }

    public OutboxRecoveryService(
            OutboxEventStore repository,
            OutboxRecoveryProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public OutboxRecoveryResult reconcileEligibleFailures() {
        if (!properties.isEnabled()) {
            return OutboxRecoveryResult.disabledResult();
        }

        Instant now = Instant.now(clock);
        List<UUID> eligibleIds = repository.findEligibleRecoveryIds(
                OutboxEventStatus.FAILED,
                OutboxFailureDisposition.TRANSIENT,
                now,
                properties.getMaxCycles(),
                properties.getBatchSize()
        );

        int requeued = 0;
        for (UUID eventId : eligibleIds) {
            Integer updated = transactionTemplate.execute(status -> repository.requeueFailedForRecovery(
                    eventId,
                    OutboxEventStatus.FAILED,
                    OutboxEventStatus.PENDING,
                    OutboxFailureDisposition.TRANSIENT,
                    now,
                    properties.getMaxCycles()
            ));
            if (updated != null && updated == 1) {
                requeued++;
            }
        }

        return new OutboxRecoveryResult(eligibleIds.size(), requeued, eligibleIds.size() - requeued, false);
    }

    @Override
    public OutboxRecoveryResult recoverStalePublishing() {
        if (!properties.isEnabled()) {
            return OutboxRecoveryResult.disabledResult();
        }

        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(properties.getStalePublishingAge());
        List<UUID> staleIds = repository.findStalePublishingIds(
                OutboxEventStatus.PUBLISHING,
                cutoff,
                properties.getBatchSize()
        );

        int requeued = 0;
        for (UUID eventId : staleIds) {
            // The conditional update decides, not this loop: a row the relay finished, or another
            // instance already recovered, no longer matches and reports zero rows changed.
            Integer updated = transactionTemplate.execute(status -> repository.requeueStalePublishing(
                    eventId,
                    OutboxEventStatus.PUBLISHING,
                    OutboxEventStatus.PENDING,
                    cutoff,
                    OutboxRecoveryOrigin.AUTOMATIC_STALE_PUBLISHING.name(),
                    now
            ));
            if (updated != null && updated == 1) {
                requeued++;
            }
        }

        return new OutboxRecoveryResult(staleIds.size(), requeued, staleIds.size() - requeued, false);
    }
}
