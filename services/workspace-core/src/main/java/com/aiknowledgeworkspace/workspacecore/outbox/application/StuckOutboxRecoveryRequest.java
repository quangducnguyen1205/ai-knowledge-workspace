package com.aiknowledgeworkspace.workspacecore.outbox.application;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record StuckOutboxRecoveryRequest(
        UUID eventId,
        String requiredEventType,
        Duration minimumPublishingAge,
        String eventTypeMismatchMessage
) {
    public StuckOutboxRecoveryRequest {
        Objects.requireNonNull(eventId, "eventId is required");
        requireText(requiredEventType, "requiredEventType");
        Objects.requireNonNull(minimumPublishingAge, "minimumPublishingAge is required");
        if (minimumPublishingAge.isNegative()) {
            throw new IllegalArgumentException("minimumPublishingAge must not be negative");
        }
        requireText(eventTypeMismatchMessage, "eventTypeMismatchMessage");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
