package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import java.util.UUID;

public record SearchAssetDetails(UUID assetId, UUID workspaceId, boolean searchable) {
}
