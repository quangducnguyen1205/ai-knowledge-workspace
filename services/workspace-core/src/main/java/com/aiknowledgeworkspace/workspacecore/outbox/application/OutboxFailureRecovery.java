package com.aiknowledgeworkspace.workspacecore.outbox.application;

public interface OutboxFailureRecovery {
    OutboxRecoveryResult reconcileEligibleFailures();
}
