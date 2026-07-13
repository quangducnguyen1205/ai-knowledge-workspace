package com.aiknowledgeworkspace.workspacecore.processing.application;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
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
