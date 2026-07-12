package com.aiknowledgeworkspace.workspacecore.outbox;

public record OutboxRecoveryResult(
        int eligible,
        int requeued,
        int skipped,
        boolean disabled
) {

    static OutboxRecoveryResult disabledResult() {
        return new OutboxRecoveryResult(0, 0, 0, true);
    }
}
