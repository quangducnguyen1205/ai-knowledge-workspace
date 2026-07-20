package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import java.util.UUID;

record AssetProcessingFailedPayload(
        UUID processingRequestId,
        String errorCode,
        String message
) implements ProcessingResultPayload {
}
