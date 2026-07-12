package com.aiknowledgeworkspace.workspacecore.outbox;

public enum OutboxFailureDisposition {
    TRANSIENT,
    PERMANENT,
    UNKNOWN,
    RECOVERY_EXHAUSTED
}
