package com.aiknowledgeworkspace.workspacecore.processing;

import java.time.Instant;
import java.util.UUID;

public record ProcessingJob(
        UUID id,
        UUID assetId,
        String fastapiTaskId,
        String fastapiVideoId,
        ProcessingJobStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
