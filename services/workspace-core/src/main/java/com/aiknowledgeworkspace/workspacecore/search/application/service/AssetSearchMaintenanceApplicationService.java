package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.api.AssetSearchMaintenanceUseCase;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchMaintenancePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AssetSearchMaintenanceApplicationService implements AssetSearchMaintenanceUseCase {

    private final TranscriptSearchMaintenancePort transcriptSearchMaintenancePort;

    AssetSearchMaintenanceApplicationService(TranscriptSearchMaintenancePort transcriptSearchMaintenancePort) {
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
