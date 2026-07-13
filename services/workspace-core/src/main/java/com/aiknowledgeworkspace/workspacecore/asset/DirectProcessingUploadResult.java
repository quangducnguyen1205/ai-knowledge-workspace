package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;

record DirectProcessingUploadResult(
        String taskId,
        String videoId,
        String rawStatus,
        ProcessingJobStatus processingStatus,
        AssetStatus assetStatus
) {
}
