package com.aiknowledgeworkspace.workspacecore.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
