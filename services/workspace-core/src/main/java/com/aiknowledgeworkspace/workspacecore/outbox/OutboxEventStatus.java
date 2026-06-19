package com.aiknowledgeworkspace.workspacecore.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
