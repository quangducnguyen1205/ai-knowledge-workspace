package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.UUID;

record AssetProcessingFailedPayload(
        UUID processingRequestId,
        String errorCode,
        String message
) implements ProcessingResultPayload {
}
