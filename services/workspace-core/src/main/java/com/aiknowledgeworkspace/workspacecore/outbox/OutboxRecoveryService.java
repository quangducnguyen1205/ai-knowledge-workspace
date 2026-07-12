package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxRecoveryService {

    private final OutboxEventRepository repository;
    private final OutboxRecoveryProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public OutboxRecoveryService(
            OutboxEventRepository repository,
            OutboxRecoveryProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this(repository, properties, transactionTemplate, Clock.systemUTC());
    }

    OutboxRecoveryService(
            OutboxEventRepository repository,
            OutboxRecoveryProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

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
                PageRequest.of(0, properties.getBatchSize())
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
}
