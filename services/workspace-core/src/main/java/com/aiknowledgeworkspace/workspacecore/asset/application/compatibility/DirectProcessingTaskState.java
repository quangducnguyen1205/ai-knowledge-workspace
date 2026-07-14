package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;

public record DirectProcessingTaskState(
        String rawStatus,
        ProcessingJobStatus processingStatus,
        AssetStatus assetStatus
) {
}
