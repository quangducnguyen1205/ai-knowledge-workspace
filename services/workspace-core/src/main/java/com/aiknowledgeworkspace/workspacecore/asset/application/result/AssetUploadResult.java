package com.aiknowledgeworkspace.workspacecore.asset.application.result;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.UUID;

public record AssetUploadResult(UUID assetId, UUID processingJobId, AssetStatus status, UUID workspaceId) {
}
