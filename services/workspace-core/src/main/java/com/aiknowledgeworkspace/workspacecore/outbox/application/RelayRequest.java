package com.aiknowledgeworkspace.workspacecore.outbox.application;

import java.util.Objects;
import java.util.UUID;

public record RelayRequest(RelaySelection selection, RelayExecutionPolicy executionPolicy) {
    public RelayRequest {
        Objects.requireNonNull(selection, "selection is required");
        Objects.requireNonNull(executionPolicy, "executionPolicy is required");
        boolean exact = selection instanceof RelaySelection.ExactEvent;
        if (exact != (executionPolicy == RelayExecutionPolicy.EXPLICIT_OPERATOR)) {
            throw new IllegalArgumentException("exact selection and explicit operator policy must be used together");
        }
    }

    public static RelayRequest scheduledAll(int batchSize) {
        return new RelayRequest(new RelaySelection.AllDue(batchSize), RelayExecutionPolicy.SCHEDULED_GLOBAL);
    }

    public static RelayRequest scheduledForType(String eventType, int batchSize) {
        return new RelayRequest(new RelaySelection.DueByType(eventType, batchSize), RelayExecutionPolicy.SCHEDULED_SCOPED);
    }

    public static RelayRequest explicit(UUID eventId, String requiredEventType, String eventTypeMismatchMessage) {
        return new RelayRequest(
                new RelaySelection.ExactEvent(eventId, requiredEventType, eventTypeMismatchMessage),
                RelayExecutionPolicy.EXPLICIT_OPERATOR
        );
    }
}
