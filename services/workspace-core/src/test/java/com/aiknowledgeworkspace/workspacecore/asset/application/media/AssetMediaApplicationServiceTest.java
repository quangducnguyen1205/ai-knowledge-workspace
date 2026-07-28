package com.aiknowledgeworkspace.workspacecore.asset.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaNotAvailableException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaReadException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetMediaDescriptor;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetMediaApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectMetadata;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectNotFoundException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReadException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetMediaApplicationServiceTest {

    private AssetQueryApplicationService assetQueries;
    private ObjectStorageUseCase objectStorage;
    private AssetMediaApplicationService mediaService;

    @BeforeEach
    void setUp() {
        assetQueries = mock(AssetQueryApplicationService.class);
        objectStorage = mock(ObjectStorageUseCase.class);
        mediaService = new AssetMediaApplicationService(assetQueries, objectStorage);
    }

    @Test
    void authorizedFailedUploadResolvesAndOpensTheOwnedObject() {
        Asset asset = uploaded(AssetStatus.FAILED, 10);
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);
        when(objectStorage.stat(any())).thenReturn(new StoredObjectMetadata(10, "video/mp4"));
        InputStream expected = new ByteArrayInputStream(new byte[]{2, 3, 4});
        when(objectStorage.openRange(any(), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(3L))).thenReturn(expected);

        AssetMediaDescriptor descriptor = mediaService.resolve(asset.getId());
        InputStream actual = mediaService.openStream(descriptor, 2, 3);

        assertThat(descriptor.assetId()).isEqualTo(asset.getId());
        assertThat(descriptor.totalSizeBytes()).isEqualTo(10);
        assertThat(descriptor.originalFilename()).isEqualTo("lecture.mp4");
        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<StoredObjectReference> referenceCaptor =
                ArgumentCaptor.forClass(StoredObjectReference.class);
        verify(objectStorage).stat(referenceCaptor.capture());
        assertThat(referenceCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(referenceCaptor.getValue().objectKey()).isEqualTo("objects/lecture.mp4");
        verify(objectStorage).openRange(referenceCaptor.capture(), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    void authorizationFailureOccursBeforeStorageAccess() {
        UUID assetId = UUID.randomUUID();
        when(assetQueries.loadAuthorizedAsset(assetId)).thenThrow(new AssetNotFoundException());

        assertThatThrownBy(() -> mediaService.resolve(assetId))
                .isInstanceOf(AssetNotFoundException.class);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void youtubeAssetDoesNotAccessObjectStorage() {
        Asset asset = Asset.youtube(
                UUID.randomUUID(),
                "abc_DEF-123",
                "YouTube",
                AssetStatus.SEARCHABLE,
                UUID.randomUUID()
        );
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);

        assertThatThrownBy(() -> mediaService.resolve(asset.getId()))
                .isInstanceOf(AssetMediaNotAvailableException.class);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void missingOrInconsistentObjectIsNotAvailable() {
        Asset missing = uploaded(AssetStatus.PROCESSING, 10);
        when(assetQueries.loadAuthorizedAsset(missing.getId())).thenReturn(missing);
        when(objectStorage.stat(any())).thenThrow(
                new StoredObjectNotFoundException("not found", new RuntimeException())
        );

        assertThatThrownBy(() -> mediaService.resolve(missing.getId()))
                .isInstanceOf(AssetMediaNotAvailableException.class);

        Asset inconsistent = uploaded(AssetStatus.SEARCHABLE, 10);
        when(assetQueries.loadAuthorizedAsset(inconsistent.getId())).thenReturn(inconsistent);
        doReturn(new StoredObjectMetadata(9, "video/mp4")).when(objectStorage).stat(any());

        assertThatThrownBy(() -> mediaService.resolve(inconsistent.getId()))
                .isInstanceOf(AssetMediaNotAvailableException.class);
    }

    @Test
    void storageReadFailureIsTranslatedWithoutStorageIdentity() {
        Asset asset = uploaded(AssetStatus.PROCESSING, 10);
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);
        when(objectStorage.stat(any())).thenThrow(
                new StoredObjectReadException("bucket=secret object=secret", new RuntimeException())
        );

        assertThatThrownBy(() -> mediaService.resolve(asset.getId()))
                .isInstanceOf(AssetMediaReadException.class)
                .hasMessage("Asset media could not be read")
                .hasMessageNotContaining("bucket")
                .hasMessageNotContaining("object");
    }

    @Test
    void storageOpenFailureIsTranslatedBeforeTheHttpTransferStarts() {
        Asset asset = uploaded(AssetStatus.PROCESSING, 10);
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);
        when(objectStorage.stat(any())).thenReturn(new StoredObjectMetadata(10, "video/mp4"));
        when(objectStorage.openRange(any(), anyLong(), anyLong())).thenThrow(
                new StoredObjectReadException("internal endpoint", new RuntimeException())
        );
        AssetMediaDescriptor descriptor = mediaService.resolve(asset.getId());

        assertThatThrownBy(() -> mediaService.openStream(descriptor, 0, 10))
                .isInstanceOf(AssetMediaReadException.class)
                .hasMessage("Asset media could not be read");
    }

    @Test
    void zeroLengthUploadIsNotStreamableAndDoesNotAccessStorage() {
        Asset asset = uploaded(AssetStatus.FAILED, 0);
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);

        assertThatThrownBy(() -> mediaService.resolve(asset.getId()))
                .isInstanceOf(AssetMediaNotAvailableException.class);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void openingOutsideResolvedBoundsIsRejectedBeforeStorageAccess() {
        Asset asset = uploaded(AssetStatus.PROCESSING, 10);
        when(assetQueries.loadAuthorizedAsset(asset.getId())).thenReturn(asset);
        when(objectStorage.stat(any())).thenReturn(new StoredObjectMetadata(10, "video/mp4"));
        AssetMediaDescriptor descriptor = mediaService.resolve(asset.getId());

        assertThatThrownBy(() -> mediaService.openStream(descriptor, 9, 2))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(objectStorage, org.mockito.Mockito.never())
                .openRange(any(), anyLong(), anyLong());
    }

    private Asset uploaded(AssetStatus status, long sizeBytes) {
        return Asset.uploaded(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture",
                status,
                UUID.randomUUID(),
                "workspace-media",
                "objects/lecture.mp4",
                "video/mp4",
                sizeBytes,
                "\"etag\""
        );
    }
}
