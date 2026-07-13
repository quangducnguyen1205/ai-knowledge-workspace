package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.List;
import java.util.UUID;

public interface SearchAssetQueryPort {
    SearchAssetDetails getAuthorizedAssetDetails(UUID assetId);

    List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId);
}
