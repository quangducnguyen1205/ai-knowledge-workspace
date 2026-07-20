package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import java.util.UUID;

public record AssetStatusResponse(
        UUID assetId,
        UUID processingJobId,
        AssetStatus assetStatus,
        ProcessingJobStatus processingJobStatus
) {
}
