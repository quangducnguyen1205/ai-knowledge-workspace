package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch.TranscriptSearchIndexClient;

import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AssetSearchMaintenanceService implements AssetSearchMaintenance {

    private final TranscriptSearchIndexClient transcriptSearchIndexClient;

    AssetSearchMaintenanceService(TranscriptSearchIndexClient transcriptSearchIndexClient) {
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
    }

    @Override
    public void deleteTranscriptRows(UUID assetId) {
        transcriptSearchIndexClient.deleteTranscriptRowsForAsset(assetId);
    }

    @Override
    public void updateAssetTitle(UUID assetId, String title) {
        transcriptSearchIndexClient.updateAssetTitle(assetId, title);
    }
}
