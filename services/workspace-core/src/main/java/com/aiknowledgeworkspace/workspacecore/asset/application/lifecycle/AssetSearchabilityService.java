package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;

import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSearchabilityService {

    private final AssetRepository assetRepository;
    private final AssetPersistenceService assetPersistenceService;

    public AssetSearchabilityService(
            AssetRepository assetRepository,
            AssetPersistenceService assetPersistenceService
    ) {
        this.assetRepository = assetRepository;
        this.assetPersistenceService = assetPersistenceService;
    }

    @Transactional
    public void markSearchable(UUID assetId) {
        assetPersistenceService.updateAssetStatus(loadAsset(assetId), AssetStatus.SEARCHABLE);
    }

    @Transactional
    public void markTranscriptReady(UUID assetId) {
        assetPersistenceService.updateAssetStatus(loadAsset(assetId), AssetStatus.TRANSCRIPT_READY);
    }

    private Asset loadAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
    }
}
