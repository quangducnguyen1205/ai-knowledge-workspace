package com.aiknowledgeworkspace.workspacecore.processing.api;

import java.util.UUID;

public record ProcessingJobView(
        UUID id,
        UUID assetId,
        ProcessingJobStatus status,
        String rawUpstreamTaskState
) {
}
