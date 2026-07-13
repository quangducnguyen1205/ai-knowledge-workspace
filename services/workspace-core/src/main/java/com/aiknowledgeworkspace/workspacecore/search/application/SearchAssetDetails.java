package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.UUID;

public record SearchAssetDetails(UUID assetId, UUID workspaceId, boolean searchable) {
}
