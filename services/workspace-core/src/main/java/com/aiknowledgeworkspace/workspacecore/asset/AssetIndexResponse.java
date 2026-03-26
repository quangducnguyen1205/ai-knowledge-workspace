package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.UUID;

public record AssetIndexResponse(
        UUID assetId,
        AssetStatus assetStatus,
        int indexedDocumentCount
) {
}
