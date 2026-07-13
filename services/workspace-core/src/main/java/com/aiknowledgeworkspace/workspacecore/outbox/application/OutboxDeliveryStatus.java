package com.aiknowledgeworkspace.workspacecore.outbox.application;

public enum OutboxDeliveryStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
