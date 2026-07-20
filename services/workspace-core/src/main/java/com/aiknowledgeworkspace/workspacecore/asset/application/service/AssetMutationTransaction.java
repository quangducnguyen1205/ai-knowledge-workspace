package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetMutationTransaction {

    private final AssetStore assetStore;
    private final CanonicalTranscriptStore transcriptStore;
    private final ProcessingRequestUseCase processingRequestUseCase;

    AssetMutationTransaction(
            AssetStore assetStore,
            CanonicalTranscriptStore transcriptStore,
            ProcessingRequestUseCase processingRequestUseCase
    ) {
        this.assetStore = assetStore;
        this.transcriptStore = transcriptStore;
        this.processingRequestUseCase = processingRequestUseCase;
    }

    @Transactional
    Asset updateTitle(Asset asset, String title) {
        asset.setTitle(title);
        return assetStore.save(asset);
    }

    @Transactional
    void delete(Asset asset) {
        transcriptStore.delete(asset.getId());
        processingRequestUseCase.deleteForAsset(asset.getId());
        assetStore.delete(asset);
    }
}
