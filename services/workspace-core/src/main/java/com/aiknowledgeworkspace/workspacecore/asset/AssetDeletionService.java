package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.search.TranscriptSearchIndexClient;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetDeletionService {

    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;

    public AssetDeletionService(
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            TranscriptSearchIndexClient transcriptSearchIndexClient
    ) {
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
    }

    public void deleteAsset(UUID assetId) {
        Asset asset = assetService.getAsset(assetId);

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            transcriptSearchIndexClient.deleteTranscriptRowsForAsset(assetId);
        }

        assetPersistenceService.deleteAssetRecords(asset);
    }
}
