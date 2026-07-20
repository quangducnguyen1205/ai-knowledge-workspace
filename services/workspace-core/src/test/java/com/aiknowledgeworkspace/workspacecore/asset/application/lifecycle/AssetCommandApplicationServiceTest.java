package com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.InvalidAssetTitleException;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetView;
import com.aiknowledgeworkspace.workspacecore.search.application.AssetSearchMaintenance;
import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
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
    private AssetSearchMaintenance searchMaintenance;

    @Mock
    private ObjectStorageApplication objectStorage;

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
        Asset asset = asset(assetId, "Old", AssetStatus.SEARCHABLE, null, null);
        Asset updated = asset(assetId, "New", AssetStatus.SEARCHABLE, null, null);
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
        Asset asset = asset(assetId, "Same", AssetStatus.SEARCHABLE, null, null);
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
        Asset asset = asset(assetId, "Asset", AssetStatus.PROCESSING, "media", "raw/video.mp4");
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
    void deleteWithoutStoredObjectStillDeletesProductTruth() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Asset", AssetStatus.FAILED, null, null);
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);

        service.delete(assetId);

        verify(searchMaintenance).deleteTranscriptRows(assetId);
        verifyNoInteractions(objectStorage);
        verify(mutationTransaction).delete(asset);
    }

    @Test
    void externalCleanupFailurePreventsDatabaseDeletion() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Asset", AssetStatus.PROCESSING, "media", "raw/video.mp4");
        when(assetQueryService.loadAuthorizedAsset(assetId)).thenReturn(asset);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable"))
                .when(objectStorage)
                .delete(any(StoredObjectReference.class));

        assertThatThrownBy(() -> service.delete(assetId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage unavailable");

        verify(mutationTransaction, never()).delete(asset);
    }

    private Asset asset(
            UUID assetId,
            String title,
            AssetStatus status,
            String bucket,
            String objectKey
    ) {
        return new Asset(
                assetId,
                "lecture.mp4",
                title,
                status,
                UUID.randomUUID(),
                bucket,
                objectKey,
                "video/mp4",
                42L,
                null
        );
    }
}
