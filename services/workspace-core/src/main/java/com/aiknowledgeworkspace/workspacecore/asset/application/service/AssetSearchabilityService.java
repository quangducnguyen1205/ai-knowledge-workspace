package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetSearchabilityService {

    private final AssetStore assetStore;

    public AssetSearchabilityService(
            AssetStore assetStore
    ) {
        this.assetStore = assetStore;
    }

    @Transactional
    public void markSearchable(UUID assetId) {
        updateStatus(loadAsset(assetId), AssetStatus.SEARCHABLE);
    }

    @Transactional
    public void markTranscriptReady(UUID assetId) {
        updateStatus(loadAsset(assetId), AssetStatus.TRANSCRIPT_READY);
    }

    private Asset loadAsset(UUID assetId) {
        return assetStore.findById(assetId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private void updateStatus(Asset asset, AssetStatus status) {
        if (asset.getStatus() != status) {
            asset.setStatus(status);
            assetStore.save(asset);
        }
    }
}
