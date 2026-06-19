package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.search.TranscriptSearchIndexClient;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetDeletionService.class);

    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;
    private final ObjectStorageClient objectStorageClient;

    public AssetDeletionService(
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            TranscriptSearchIndexClient transcriptSearchIndexClient,
            ObjectStorageClient objectStorageClient
    ) {
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
        this.objectStorageClient = objectStorageClient;
    }

    public void deleteAsset(UUID assetId) {
        Asset asset = assetService.getAsset(assetId);

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            transcriptSearchIndexClient.deleteTranscriptRowsForAsset(assetId);
        }

        assetPersistenceService.deleteAssetRecords(asset);
        deleteStoredObjectBestEffort(asset);
    }

    private void deleteStoredObjectBestEffort(Asset asset) {
        if (!StringUtils.hasText(asset.getStorageBucket()) || !StringUtils.hasText(asset.getObjectKey())) {
            return;
        }

        try {
            objectStorageClient.delete(asset.getStorageBucket(), asset.getObjectKey());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to delete stored object {}/{} for asset {}",
                    asset.getStorageBucket(),
                    asset.getObjectKey(),
                    asset.getId(),
                    exception
            );
        }
    }
}
