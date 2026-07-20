package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import java.util.UUID;

public record AssetStatusView(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        ProcessingJobStatus processingStatus
) {
}
