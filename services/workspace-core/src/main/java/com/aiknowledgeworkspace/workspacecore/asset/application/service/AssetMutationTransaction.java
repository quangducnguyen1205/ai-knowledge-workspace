package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentAssetCleanupUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetMutationTransaction {

    private final AssetStore assetStore;
    private final CanonicalTranscriptStore transcriptStore;
    private final AssetPlaybackProgressStore playbackProgressStore;
    private final ProcessingRequestUseCase processingRequestUseCase;
    private final SavedMomentAssetCleanupUseCase savedMomentCleanup;

    AssetMutationTransaction(
            AssetStore assetStore,
            CanonicalTranscriptStore transcriptStore,
            AssetPlaybackProgressStore playbackProgressStore,
            ProcessingRequestUseCase processingRequestUseCase,
            SavedMomentAssetCleanupUseCase savedMomentCleanup
    ) {
        this.assetStore = assetStore;
        this.transcriptStore = transcriptStore;
        this.playbackProgressStore = playbackProgressStore;
        this.processingRequestUseCase = processingRequestUseCase;
        this.savedMomentCleanup = savedMomentCleanup;
    }

    @Transactional
    Asset updateTitle(Asset asset, String title) {
        asset.setTitle(title);
        return assetStore.save(asset);
    }

    @Transactional
    void delete(Asset asset) {
        transcriptStore.delete(asset.getId());
        playbackProgressStore.deleteForAsset(asset.getId());
        savedMomentCleanup.deleteForAsset(asset.getId());
        processingRequestUseCase.deleteForAsset(asset.getId());
        assetStore.delete(asset);
    }
}
