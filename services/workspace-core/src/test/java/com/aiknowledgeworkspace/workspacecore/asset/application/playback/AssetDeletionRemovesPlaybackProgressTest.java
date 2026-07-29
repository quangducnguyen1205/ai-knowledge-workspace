package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Asset deletion must also remove user playback progress, without changing the existing
 * source-aware deletion ownership for Upload or YouTube Assets.
 */
class AssetDeletionRemovesPlaybackProgressTest {

    private final AssetStore assetStore = mock(AssetStore.class);
    private final CanonicalTranscriptStore transcriptStore = mock(CanonicalTranscriptStore.class);
    private final AssetPlaybackProgressStore playbackProgressStore = mock(AssetPlaybackProgressStore.class);
    private final ProcessingRequestUseCase processingRequestUseCase = mock(ProcessingRequestUseCase.class);
    private final AssetMutationTransaction transaction = new AssetMutationTransaction(
            assetStore, transcriptStore, playbackProgressStore, processingRequestUseCase
    );

    @Test
    void deletingAnUploadRemovesProgressInsideTheExistingProductTruthTransaction() {
        UUID assetId = UUID.randomUUID();
        Asset upload = Asset.uploaded(
                assetId, "lecture.mp4", "Uploaded lecture", AssetStatus.SEARCHABLE, UUID.randomUUID(),
                "workspace-media", "objects/lecture.mp4", "video/mp4", 42L, "etag-1"
        );

        transaction.delete(upload);

        InOrder order = inOrder(transcriptStore, playbackProgressStore, processingRequestUseCase, assetStore);
        order.verify(transcriptStore).delete(assetId);
        order.verify(playbackProgressStore).deleteForAsset(assetId);
        order.verify(processingRequestUseCase).deleteForAsset(assetId);
        order.verify(assetStore).delete(upload);
    }

    @Test
    void deletingAYoutubeAssetRemovesProgressThroughTheSameBoundary() {
        UUID assetId = UUID.randomUUID();
        Asset youtube = Asset.youtube(
                assetId, "abc_DEF-123", "YouTube video", AssetStatus.SEARCHABLE, UUID.randomUUID()
        );

        transaction.delete(youtube);

        verify(transcriptStore).delete(assetId);
        verify(playbackProgressStore).deleteForAsset(assetId);
        verify(processingRequestUseCase).deleteForAsset(assetId);
        verify(assetStore).delete(youtube);
    }

    @Test
    void updatingATitleNeverTouchesPlaybackProgress() {
        Asset upload = Asset.uploaded(
                UUID.randomUUID(), "lecture.mp4", "Old", AssetStatus.SEARCHABLE, UUID.randomUUID(),
                "workspace-media", "objects/lecture.mp4", "video/mp4", 42L, "etag-1"
        );

        transaction.updateTitle(upload, "New");

        org.mockito.Mockito.verifyNoInteractions(playbackProgressStore);
    }
}
