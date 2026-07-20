package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.InvalidAssetTitleException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetView;
import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetCommandApplicationService implements AssetCommandUseCase {

    private static final int MAX_ASSET_TITLE_LENGTH = 255;

    private final AssetQueryApplicationService assetQueryService;
    private final AssetMutationTransaction mutationTransaction;
    private final AssetSearchMaintenance searchMaintenance;
    private final ObjectStorageApplication objectStorage;

    public AssetCommandApplicationService(
            AssetQueryApplicationService assetQueryService,
            AssetMutationTransaction mutationTransaction,
            AssetSearchMaintenance searchMaintenance,
            ObjectStorageApplication objectStorage
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
