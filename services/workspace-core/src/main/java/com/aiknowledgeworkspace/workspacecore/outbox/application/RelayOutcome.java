package com.aiknowledgeworkspace.workspacecore.outbox.application;

import java.util.Objects;
import java.util.Optional;

public record RelayOutcome(int processedCount, Optional<OutboxDeliveryStatus> deliveryStatus) {
    public RelayOutcome {
        if (processedCount < 0) {
            throw new IllegalArgumentException("processedCount must not be negative");
        }
        deliveryStatus = Objects.requireNonNull(deliveryStatus, "deliveryStatus is required");
    }

    public static RelayOutcome batch(int processedCount) {
        return new RelayOutcome(processedCount, Optional.empty());
    }

    public static RelayOutcome single(OutboxDeliveryStatus status) {
        return new RelayOutcome(1, Optional.of(Objects.requireNonNull(status, "status is required")));
    }

    public OutboxDeliveryStatus requiredDeliveryStatus() {
        return deliveryStatus.orElseThrow(() -> new IllegalStateException("Relay outcome does not contain a delivery status"));
    }
}
