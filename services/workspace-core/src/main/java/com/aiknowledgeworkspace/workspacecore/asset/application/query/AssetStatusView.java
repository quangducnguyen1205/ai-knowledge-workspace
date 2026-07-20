package com.aiknowledgeworkspace.workspacecore.asset.application.query;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import java.util.UUID;

public record AssetStatusView(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        ProcessingJobStatus processingStatus
) {
}
