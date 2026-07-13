package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.UUID;

public interface AssetSearchMaintenance {
    void deleteTranscriptRows(UUID assetId);

    void updateAssetTitle(UUID assetId, String title);
}
