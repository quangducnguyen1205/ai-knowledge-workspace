package com.aiknowledgeworkspace.workspacecore.outbox.api;

public interface OutboxFailureRecovery {

    /** Requeues transient failures whose cooldown has elapsed. */
    OutboxRecoveryResult reconcileEligibleFailures();

    /**
     * Requeues events left claimed for publication by a relay that never came back. Publication
     * stays with the normal relay: this only returns the row to the state the relay owns.
     */
    OutboxRecoveryResult recoverStalePublishing();
}
