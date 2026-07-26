package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetMutationTransaction;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetCommandApplicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidAssetTitleException;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import com.aiknowledgeworkspace.workspacecore.search.api.AssetSearchMaintenanceUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetCommandApplicationServiceTest {

    @Mock
    private AssetQueryApplicationService assetQueryService;

    @Mock
    private AssetMutationTransaction mutationTransaction;

    @Mock
    private AssetSearchMaintenanceUseCase searchMaintenance;

    @Mock
    private ObjectStorageUseCase objectStorage;

    private AssetCommandApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssetCommandApplicationService(
                assetQueryService,
                mutationTransaction,
                searchMaintenance,
                objectStorage
        );
    }

    @Test
    void searchableTitleUpdateSynchronizesDerivedIndexBeforeProductTruth() {
        UUID assetId = UUID.randomUUID();
        Asset asset = uploadedAsset(assetId, "Old", AssetStatus.SEARCHABLE);
        Asset updated = uploadedAsset(assetId, "New", AssetStatus.SEARCHABLE);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);
        when(mutationTransaction.updateTitle(asset, "New")).thenReturn(updated);

        AssetView result = service.updateTitle(assetId, "  New  ");

        assertThat(result.title()).isEqualTo("New");
        InOrder order = inOrder(searchMaintenance, mutationTransaction);
        order.verify(searchMaintenance).updateAssetTitle(assetId, "New");
        order.verify(mutationTransaction).updateTitle(asset, "New");
    }

    @Test
    void unchangedTitleIsANoOp() {
        UUID assetId = UUID.randomUUID();
        Asset asset = uploadedAsset(assetId, "Same", AssetStatus.SEARCHABLE);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);

        AssetView result = service.updateTitle(assetId, " Same ");

        assertThat(result.title()).isEqualTo("Same");
        verifyNoInteractions(searchMaintenance, mutationTransaction, objectStorage);
    }

    @Test
    void invalidTitleIsRejectedBeforeLoadingTheAsset() {
        assertThatThrownBy(() -> service.updateTitle(UUID.randomUUID(), "   "))
                .isInstanceOf(InvalidAssetTitleException.class)
                .hasMessage("title must not be blank");
        verifyNoInteractions(assetQueryService, mutationTransaction, searchMaintenance, objectStorage);
    }

    @Test
    void deleteCleansExternalResourcesBeforeDeletingProductTruth() {
        UUID assetId = UUID.randomUUID();
        Asset asset = uploadedAsset(assetId, "Asset", AssetStatus.PROCESSING);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);

        service.delete(assetId);

        InOrder order = inOrder(searchMaintenance, objectStorage, mutationTransaction);
        order.verify(searchMaintenance).deleteTranscriptRows(assetId);
        order.verify(objectStorage).delete(argThat(reference ->
                "media".equals(reference.bucket()) && "raw/video.mp4".equals(reference.objectKey())
        ));
        order.verify(mutationTransaction).delete(asset);
    }

    @Test
    void youtubeDeletionSkipsStorageAndStillDeletesProductTruth() {
        UUID assetId = UUID.randomUUID();
        Asset asset = Asset.youtube(
                assetId, "video-id", "Asset", AssetStatus.FAILED, UUID.randomUUID()
        );
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);

        service.delete(assetId);

        verify(searchMaintenance).deleteTranscriptRows(assetId);
        verifyNoInteractions(objectStorage);
        verify(mutationTransaction).delete(asset);
    }

    @Test
    void externalCleanupFailurePreventsDatabaseDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = uploadedAsset(assetId, "Asset", AssetStatus.PROCESSING);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable"))
                .when(objectStorage)
                .delete(any(StoredObjectReference.class));

        assertThatThrownBy(() -> service.delete(assetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage unavailable");

        verify(searchMaintenance).deleteTranscriptRows(assetId);
        verify(mutationTransaction, never()).delete(asset);
    }

    @Test
    void searchCleanupFailureStopsStorageAndDatabaseDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = uploadedAsset(assetId, "Asset", AssetStatus.SEARCHABLE);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);
        org.mockito.Mockito.doThrow(new IllegalStateException("search unavailable"))
                .when(searchMaintenance)
                .deleteTranscriptRows(assetId);

        assertThatThrownBy(() -> service.delete(assetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("search unavailable");

        verifyNoInteractions(objectStorage);
        verify(mutationTransaction, never()).delete(asset);
    }

    @Test
    void youtubeSearchCleanupFailureStillPreventsDatabaseDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = Asset.youtube(
                assetId, "video-id", "Asset", AssetStatus.SEARCHABLE, UUID.randomUUID()
        );
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);
        org.mockito.Mockito.doThrow(new IllegalStateException("search unavailable"))
                .when(searchMaintenance)
                .deleteTranscriptRows(assetId);

        assertThatThrownBy(() -> service.delete(assetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("search unavailable");

        verifyNoInteractions(objectStorage);
        verify(mutationTransaction, never()).delete(asset);
    }

    private Asset uploadedAsset(UUID assetId, String title, AssetStatus status) {
        return Asset.uploaded(
                assetId,
                "lecture.mp4",
                title,
                status,
                UUID.randomUUID(),
                "media",
                "raw/video.mp4",
                "video/mp4",
                42L,
                null
        );
    }
}
