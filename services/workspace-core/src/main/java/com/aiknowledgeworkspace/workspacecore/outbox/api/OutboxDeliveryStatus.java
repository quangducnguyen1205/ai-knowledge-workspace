package com.aiknowledgeworkspace.workspacecore.outbox.api;

public enum OutboxDeliveryStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
