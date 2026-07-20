package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadCommand;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.SupportedUploadMediaPolicy;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.UploadAssetApplicationService;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetUploadTransaction;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidUploadRequestException;
import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadAssetApplicationServiceTest {

    @Mock
    private AssetUploadTransaction transaction;

    @Mock
    private WorkspaceAccessUseCase workspaceAccess;

    @Mock
    private ObjectStorageUseCase objectStorage;

    private UploadAssetApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UploadAssetApplicationService(
                transaction, workspaceAccess, objectStorage, new SupportedUploadMediaPolicy()
        );
    }

    @Test
    void normalUploadStoresObjectThenPersistsAssetJobAndOutboxIntent() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        WorkspaceAccess access = new WorkspaceAccess(workspaceId, "owner-1");
        StoredObjectReference stored = new StoredObjectReference(
                "media", "owner/workspace/asset/video.mp4", 12L, "video/mp4", "etag"
        );
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId)).thenReturn(access);
        when(objectStorage.store(any(StoreObjectCommand.class))).thenReturn(stored);
        when(transaction.persist(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq("lecture.mp4"),
                org.mockito.ArgumentMatchers.eq("Lecture"),
                org.mockito.ArgumentMatchers.eq(workspaceId),
                org.mockito.ArgumentMatchers.eq("owner-1"),
                org.mockito.ArgumentMatchers.same(stored)
        )).thenReturn(new AssetUploadResult(assetId, jobId, AssetStatus.PROCESSING, workspaceId));

        AssetUploadResult result = service.upload(command(workspaceId, "Lecture", mp4Bytes()));

        assertThat(result.assetId()).isEqualTo(assetId);
        verify(objectStorage).store(any(StoreObjectCommand.class));
        verify(transaction).persist(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq("lecture.mp4"),
                org.mockito.ArgumentMatchers.eq("Lecture"),
                org.mockito.ArgumentMatchers.eq(workspaceId),
                org.mockito.ArgumentMatchers.eq("owner-1"),
                org.mockito.ArgumentMatchers.same(stored)
        );
    }

    @Test
    void databaseFailureTriggersBestEffortObjectCleanup() {
        UUID workspaceId = UUID.randomUUID();
        StoredObjectReference stored = new StoredObjectReference("media", "raw/video.mp4", 12L, "video/mp4", null);
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, "owner-1"));
        when(objectStorage.store(any())).thenReturn(stored);
        doThrow(new IllegalStateException("database unavailable"))
                .when(transaction)
                .persist(
                        any(UUID.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(workspaceId),
                        org.mockito.ArgumentMatchers.eq("owner-1"),
                        org.mockito.ArgumentMatchers.same(stored)
                );

        assertThatThrownBy(() -> service.upload(command(workspaceId, null, mp4Bytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(objectStorage).delete(stored);
    }

    @Test
    void emptyContentIsRejectedBeforeAnyBoundaryCall() {
        AssetUploadCommand command = new AssetUploadCommand(
                UUID.randomUUID(), "lecture.mp4", "video/mp4", 0L, null,
                () -> new ByteArrayInputStream(new byte[0])
        );

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(InvalidUploadRequestException.class)
                .hasMessage("A non-empty file is required");

        verifyNoInteractions(workspaceAccess, objectStorage, transaction);
    }

    private AssetUploadCommand command(UUID workspaceId, String title, byte[] bytes) {
        return new AssetUploadCommand(
                workspaceId,
                "lecture.mp4",
                "video/mp4",
                bytes.length,
                title,
                () -> new ByteArrayInputStream(bytes)
        );
    }

    private byte[] mp4Bytes() {
        return new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }
}
