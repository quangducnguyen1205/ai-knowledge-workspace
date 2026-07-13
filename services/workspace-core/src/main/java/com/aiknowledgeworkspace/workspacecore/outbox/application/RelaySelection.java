package com.aiknowledgeworkspace.workspacecore.outbox.application;

import java.util.Objects;
import java.util.UUID;

public sealed interface RelaySelection
        permits RelaySelection.AllDue, RelaySelection.DueByType, RelaySelection.ExactEvent {

    record AllDue(int batchSize) implements RelaySelection {
        public AllDue {
            requirePositiveBatchSize(batchSize);
        }
    }

    record DueByType(String eventType, int batchSize) implements RelaySelection {
        public DueByType {
            requireText(eventType, "eventType");
            requirePositiveBatchSize(batchSize);
        }
    }

    record ExactEvent(UUID eventId, String requiredEventType, String eventTypeMismatchMessage)
            implements RelaySelection {
        public ExactEvent {
            Objects.requireNonNull(eventId, "eventId is required");
            requireText(requiredEventType, "requiredEventType");
            requireText(eventTypeMismatchMessage, "eventTypeMismatchMessage");
        }
    }

    private static void requirePositiveBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
