package com.aiknowledgeworkspace.workspacecore.processing.application.model;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEventStatus;

import java.util.UUID;

public record ProcessingResultHandleResult(
        UUID eventId,
        ConsumedProcessingResultEventStatus status,
        boolean applied
) {
}
