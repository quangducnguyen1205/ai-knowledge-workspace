package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetMutationTransaction {

    private final AssetStore assetStore;
    private final CanonicalTranscriptStore transcriptStore;
    private final ProcessingRequestApplication processingRequestApplication;

    AssetMutationTransaction(
            AssetStore assetStore,
            CanonicalTranscriptStore transcriptStore,
            ProcessingRequestApplication processingRequestApplication
    ) {
        this.assetStore = assetStore;
        this.transcriptStore = transcriptStore;
        this.processingRequestApplication = processingRequestApplication;
    }

    @Transactional
    Asset updateTitle(Asset asset, String title) {
        asset.setTitle(title);
        return assetStore.save(asset);
    }

    @Transactional
    void delete(Asset asset) {
        transcriptStore.delete(asset.getId());
        processingRequestApplication.deleteForAsset(asset.getId());
        assetStore.delete(asset);
    }
}
