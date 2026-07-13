package com.aiknowledgeworkspace.workspacecore.outbox.application;

public record OutboxRecoveryResult(int eligible, int requeued, int skipped, boolean disabled) {
    public static OutboxRecoveryResult disabledResult() {
        return new OutboxRecoveryResult(0, 0, 0, true);
    }
}
