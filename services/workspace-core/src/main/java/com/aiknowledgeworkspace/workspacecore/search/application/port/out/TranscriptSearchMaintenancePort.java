package com.aiknowledgeworkspace.workspacecore.search.application.port.out;

import java.util.UUID;

public interface TranscriptSearchMaintenancePort {

    void deleteTranscriptRows(UUID assetId);

    void updateAssetTitle(UUID assetId, String title);
}
