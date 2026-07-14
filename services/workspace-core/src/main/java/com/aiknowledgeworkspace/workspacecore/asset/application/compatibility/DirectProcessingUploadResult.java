package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;

public record DirectProcessingUploadResult(
        String taskId,
        String videoId,
        String rawStatus,
        ProcessingJobStatus processingStatus,
        AssetStatus assetStatus
) {
}
