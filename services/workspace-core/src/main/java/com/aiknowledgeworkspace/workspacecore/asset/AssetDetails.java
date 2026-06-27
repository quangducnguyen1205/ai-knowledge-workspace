package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.UUID;

public record AssetDetails(
        UUID assetId,
        UUID workspaceId,
        String title,
        AssetStatus status
) {

    public boolean searchable() {
        return status == AssetStatus.SEARCHABLE;
    }
}
