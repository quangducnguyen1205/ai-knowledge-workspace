package com.aiknowledgeworkspace.workspacecore.outbox.adapter.in.scheduling;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxFailureRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRecoveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "outbox.recovery", name = "enabled", havingValue = "true")
public class OutboxRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRecoveryScheduler.class);

    private final OutboxFailureRecovery recoveryService;

    public OutboxRecoveryScheduler(OutboxFailureRecovery recoveryService) {
        this.recoveryService = recoveryService;
    }

    public void reconcileOnSchedule() {
        reconcileEligibleFailures();
        recoverStalePublishing();
    }

    private void reconcileEligibleFailures() {
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

    /**
     * Kept independent of the failure pass: a database error in one must not skip the other, and
     * either is safe to retry on the next tick because both are conditional on current row state.
     */
    private void recoverStalePublishing() {
        try {
            OutboxRecoveryResult result = recoveryService.recoverStalePublishing();
            if (result.eligible() > 0) {
                // Counts only — the event ids are already in the lifecycle logs and the rows.
                LOGGER.info(
                        "Stale outbox publishing recovery completed eligible={} requeued={} skipped={}",
                        result.eligible(),
                        result.requeued(),
                        result.skipped()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Stale outbox publishing recovery failed category={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
