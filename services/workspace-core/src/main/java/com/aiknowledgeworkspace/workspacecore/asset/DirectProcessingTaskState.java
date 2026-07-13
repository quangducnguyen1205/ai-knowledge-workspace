package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;

record DirectProcessingTaskState(
        String rawStatus,
        ProcessingJobStatus processingStatus,
        AssetStatus assetStatus
) {
}
