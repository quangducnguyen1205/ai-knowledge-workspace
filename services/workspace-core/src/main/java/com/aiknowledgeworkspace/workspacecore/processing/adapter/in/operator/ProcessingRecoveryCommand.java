package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.operator;

public enum ProcessingRecoveryCommand {
    NONE,
    RETRY_FAILED_RESULT_EVENT_ONCE,
    REQUEUE_STUCK_OUTBOX_EVENT_ONCE
}
