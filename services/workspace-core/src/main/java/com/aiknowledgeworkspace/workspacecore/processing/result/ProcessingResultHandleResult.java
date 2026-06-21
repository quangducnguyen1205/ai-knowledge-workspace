package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.UUID;

public record ProcessingResultHandleResult(
        UUID eventId,
        ConsumedProcessingResultEventStatus status,
        boolean applied
) {
}
