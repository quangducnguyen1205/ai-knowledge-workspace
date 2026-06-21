package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.UUID;

record TranscriptReadyPayload(UUID processingRequestId) implements ProcessingResultPayload {
}
