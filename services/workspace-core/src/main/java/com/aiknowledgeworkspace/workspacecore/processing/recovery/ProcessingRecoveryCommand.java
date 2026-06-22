package com.aiknowledgeworkspace.workspacecore.processing.recovery;

public enum ProcessingRecoveryCommand {
    NONE,
    RETRY_FAILED_RESULT_EVENT_ONCE,
    REQUEUE_STUCK_OUTBOX_EVENT_ONCE
}
