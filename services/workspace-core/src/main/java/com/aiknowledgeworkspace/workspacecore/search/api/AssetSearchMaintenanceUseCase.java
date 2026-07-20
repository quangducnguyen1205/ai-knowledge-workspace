package com.aiknowledgeworkspace.workspacecore.search.api;

import java.util.UUID;

public interface AssetSearchMaintenanceUseCase {
    void deleteTranscriptRows(UUID assetId);

    void updateAssetTitle(UUID assetId, String title);
}
