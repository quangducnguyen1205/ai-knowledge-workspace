package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import java.util.UUID;

record TranscriptReadyPayload(UUID processingRequestId) implements ProcessingResultPayload {
}
