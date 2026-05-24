package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.search.TranscriptSearchIndexClient;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetTitleUpdateService {

    private static final int MAX_ASSET_TITLE_LENGTH = 255;

    private final AssetService assetService;
    private final AssetPersistenceService assetPersistenceService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;

    public AssetTitleUpdateService(
            AssetService assetService,
            AssetPersistenceService assetPersistenceService,
            TranscriptSearchIndexClient transcriptSearchIndexClient
    ) {
        this.assetService = assetService;
        this.assetPersistenceService = assetPersistenceService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
    }

    public Asset updateAssetTitle(UUID assetId, UpdateAssetTitleRequest request) {
        String normalizedTitle = normalizeTitle(request);
        Asset asset = assetService.getAsset(assetId);

        if (normalizedTitle.equals(asset.getTitle())) {
            return asset;
        }

        if (asset.getStatus() == AssetStatus.SEARCHABLE) {
            transcriptSearchIndexClient.updateAssetTitle(assetId, normalizedTitle);
        }

        return assetPersistenceService.updateAssetTitle(asset, normalizedTitle);
    }

    private String normalizeTitle(UpdateAssetTitleRequest request) {
        if (request == null || request.title() == null) {
            throw new InvalidAssetTitleException("title is required");
        }

        String normalizedTitle = request.title().trim();
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new InvalidAssetTitleException("title must not be blank");
        }
        if (normalizedTitle.length() > MAX_ASSET_TITLE_LENGTH) {
            throw new InvalidAssetTitleException(
                    "title must be less than or equal to " + MAX_ASSET_TITLE_LENGTH + " characters"
            );
        }
        return normalizedTitle;
    }
}
