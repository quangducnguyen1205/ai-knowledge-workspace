package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidAssetTitleException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import com.aiknowledgeworkspace.workspacecore.search.api.AssetSearchMaintenanceUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetCommandApplicationService implements AssetCommandUseCase {

    private static final int MAX_ASSET_TITLE_LENGTH = 255;

    private final AssetQueryApplicationService assetQueryService;
    private final AssetMutationTransaction mutationTransaction;
    private final AssetSearchMaintenanceUseCase searchMaintenance;
    private final ObjectStorageUseCase objectStorage;

    public AssetCommandApplicationService(
            AssetQueryApplicationService assetQueryService,
            AssetMutationTransaction mutationTransaction,
            AssetSearchMaintenanceUseCase searchMaintenance,
            ObjectStorageUseCase objectStorage
    ) {
        this.assetQueryService = assetQueryService;
        this.mutationTransaction = mutationTransaction;
        this.searchMaintenance = searchMaintenance;
        this.objectStorage = objectStorage;
    }

    @Override
    public AssetView updateTitle(UUID assetId, String requestedTitle) {
        String title = normalizeTitle(requestedTitle);
        Asset asset = assetQueryService.loadAuthorizedAsset(assetId);
        if (title.equals(asset.getTitle())) {
            return AssetView.from(asset);
        }
        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            searchMaintenance.updateAssetTitle(assetId, title);
        }
        return AssetView.from(mutationTransaction.updateTitle(asset, title));
    }

    @Override
    public void delete(UUID assetId) {
        Asset asset = assetQueryService.loadAuthorizedAsset(assetId);
        searchMaintenance.deleteTranscriptRows(assetId);
        deleteStoredObject(asset);
        mutationTransaction.delete(asset);
    }

    private String normalizeTitle(String requestedTitle) {
        if (requestedTitle == null) {
            throw new InvalidAssetTitleException("title is required");
        }
        String title = requestedTitle.trim();
        if (!StringUtils.hasText(title)) {
            throw new InvalidAssetTitleException("title must not be blank");
        }
        if (title.length() > MAX_ASSET_TITLE_LENGTH) {
            throw new InvalidAssetTitleException(
                    "title must be less than or equal to " + MAX_ASSET_TITLE_LENGTH + " characters"
            );
        }
        return title;
    }

    private void deleteStoredObject(Asset asset) {
        if (!StringUtils.hasText(asset.getStorageBucket()) || !StringUtils.hasText(asset.getObjectKey())) {
            return;
        }
        objectStorage.delete(new StoredObjectReference(
                asset.getStorageBucket(), asset.getObjectKey(), 0L, null, null
        ));
    }
}
