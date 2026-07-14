package com.aiknowledgeworkspace.workspacecore.processing.application;

import java.util.UUID;

public record ProcessingJobView(
        UUID id,
        UUID assetId,
        String fastapiTaskId,
        String fastapiVideoId,
        ProcessingJobStatus status,
        String rawUpstreamTaskState
) {
}
