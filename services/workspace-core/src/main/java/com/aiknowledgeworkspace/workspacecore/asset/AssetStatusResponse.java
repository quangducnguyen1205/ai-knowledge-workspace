package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.UUID;

public record AssetStatusResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        ProcessingJobStatus processingJobStatus
) {
}
