package com.aiknowledgeworkspace.workspacecore.outbox.domain;

public record OutboxFailureClassification(
        OutboxFailureDisposition disposition,
        String safeCategory
) {
}
