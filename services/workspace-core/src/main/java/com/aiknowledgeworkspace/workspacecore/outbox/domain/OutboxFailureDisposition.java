package com.aiknowledgeworkspace.workspacecore.outbox.domain;

public enum OutboxFailureDisposition {
    TRANSIENT,
    PERMANENT,
    UNKNOWN,
    RECOVERY_EXHAUSTED
}
