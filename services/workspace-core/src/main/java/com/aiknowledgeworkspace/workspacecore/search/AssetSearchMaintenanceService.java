package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchMaintenancePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AssetSearchMaintenanceService implements AssetSearchMaintenance {

    private final TranscriptSearchMaintenancePort transcriptSearchMaintenancePort;

    AssetSearchMaintenanceService(TranscriptSearchMaintenancePort transcriptSearchMaintenancePort) {
        this.transcriptSearchMaintenancePort = transcriptSearchMaintenancePort;
    }

    @Override
    public void deleteTranscriptRows(UUID assetId) {
        transcriptSearchMaintenancePort.deleteTranscriptRows(assetId);
    }

    @Override
    public void updateAssetTitle(UUID assetId, String title) {
        transcriptSearchMaintenancePort.updateAssetTitle(assetId, title);
    }
}
