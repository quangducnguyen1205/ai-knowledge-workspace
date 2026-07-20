package com.aiknowledgeworkspace.workspacecore.outbox.api;

public interface OutboxFailureRecovery {
    OutboxRecoveryResult reconcileEligibleFailures();
}
