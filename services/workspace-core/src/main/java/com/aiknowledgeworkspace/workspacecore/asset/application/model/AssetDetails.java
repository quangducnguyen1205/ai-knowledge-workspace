package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

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
