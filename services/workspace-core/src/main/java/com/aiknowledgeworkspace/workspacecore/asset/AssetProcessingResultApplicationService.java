package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetProcessingResultApplicationService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;

    public AssetProcessingResultApplicationService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
    }

    @Transactional
    public void applyTranscriptReady(UUID assetId, List<AssetTranscriptRowInput> transcriptRows) {
        Asset asset = loadAsset(assetId);
        assetPersistenceService.replaceTranscriptSnapshot(asset, transcriptRows);
        if (asset.getStatus() != AssetStatus.SEARCHABLE) {
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        }
    }

    @Transactional
    public void applyProcessingFailed(UUID assetId) {
        Asset asset = loadAsset(assetId);
        if (asset.getStatus() != AssetStatus.FAILED) {
            assetPersistenceService.updateAssetStatus(asset, AssetStatus.FAILED);
        }
    }

    private Asset loadAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
    }
}
