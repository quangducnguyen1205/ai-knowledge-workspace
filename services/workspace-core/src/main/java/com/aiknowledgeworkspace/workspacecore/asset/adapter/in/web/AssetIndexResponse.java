package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import java.util.UUID;

public record AssetIndexResponse(
        UUID assetId,
        AssetStatus assetStatus,
        int indexedDocumentCount
) {
}
