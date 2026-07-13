package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetDeletionService.class);

    private final AssetQueryApplicationService assetQueryApplicationService;
    private final AssetPersistenceService assetPersistenceService;
    private final AssetSearchMaintenance assetSearchMaintenance;
    private final ObjectStorageClient objectStorageClient;

    public AssetDeletionService(
            AssetQueryApplicationService assetQueryApplicationService,
            AssetPersistenceService assetPersistenceService,
            AssetSearchMaintenance assetSearchMaintenance,
            ObjectStorageClient objectStorageClient
    ) {
        this.assetQueryApplicationService = assetQueryApplicationService;
        this.assetPersistenceService = assetPersistenceService;
        this.assetSearchMaintenance = assetSearchMaintenance;
        this.objectStorageClient = objectStorageClient;
    }

    public void deleteAsset(UUID assetId) {
        Asset asset = assetQueryApplicationService.getAsset(assetId);

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            assetSearchMaintenance.deleteTranscriptRows(assetId);
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
