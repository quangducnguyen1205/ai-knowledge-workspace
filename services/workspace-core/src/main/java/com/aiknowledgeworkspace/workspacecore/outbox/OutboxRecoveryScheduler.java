package com.aiknowledgeworkspace.workspacecore.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "outbox.recovery", name = "enabled", havingValue = "true")
public class OutboxRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRecoveryScheduler.class);

    private final OutboxRecoveryService recoveryService;

    public OutboxRecoveryScheduler(OutboxRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public void reconcileOnSchedule() {
        try {
            OutboxRecoveryResult result = recoveryService.reconcileEligibleFailures();
            if (result.eligible() > 0) {
                LOGGER.info(
                        "Outbox recovery reconciliation completed eligible={} requeued={} skipped={}",
                        result.eligible(),
                        result.requeued(),
                        result.skipped()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Outbox recovery reconciliation failed category={}", exception.getClass().getSimpleName());
        }
    }
}
