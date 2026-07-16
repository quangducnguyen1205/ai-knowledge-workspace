package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;

import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetDeletionService {

    private final AssetQueryApplicationService assetQueryApplicationService;
    private final AssetPersistenceService assetPersistenceService;
    private final AssetSearchMaintenance assetSearchMaintenance;
    private final ObjectStorageApplication objectStorageApplication;

    public AssetDeletionService(
            AssetQueryApplicationService assetQueryApplicationService,
            AssetPersistenceService assetPersistenceService,
            AssetSearchMaintenance assetSearchMaintenance,
            ObjectStorageApplication objectStorageApplication
    ) {
        this.assetQueryApplicationService = assetQueryApplicationService;
        this.assetPersistenceService = assetPersistenceService;
        this.assetSearchMaintenance = assetSearchMaintenance;
        this.objectStorageApplication = objectStorageApplication;
    }

    public void deleteAsset(UUID assetId) {
        Asset asset = assetQueryApplicationService.getAsset(assetId);

        assetSearchMaintenance.deleteTranscriptRows(assetId);
        deleteStoredObject(asset);
        assetPersistenceService.deleteAssetRecords(asset);
    }

    private void deleteStoredObject(Asset asset) {
        if (!StringUtils.hasText(asset.getStorageBucket()) || !StringUtils.hasText(asset.getObjectKey())) {
            return;
        }

        objectStorageApplication.delete(new StoredObjectReference(
                asset.getStorageBucket(), asset.getObjectKey(), 0L, null, null
        ));
    }
}
