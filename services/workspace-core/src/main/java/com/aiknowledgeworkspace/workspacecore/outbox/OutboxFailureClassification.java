package com.aiknowledgeworkspace.workspacecore.outbox;

public record OutboxFailureClassification(
        OutboxFailureDisposition disposition,
        String safeCategory
) {
}
