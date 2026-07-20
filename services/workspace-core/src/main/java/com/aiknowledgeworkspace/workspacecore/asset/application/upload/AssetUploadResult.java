package com.aiknowledgeworkspace.workspacecore.asset.application.upload;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import java.util.UUID;

public record AssetUploadResult(UUID assetId, UUID processingJobId, AssetStatus status, UUID workspaceId) {
}
