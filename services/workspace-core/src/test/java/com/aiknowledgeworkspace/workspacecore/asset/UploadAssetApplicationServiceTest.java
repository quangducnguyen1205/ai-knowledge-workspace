package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.SupportedUploadMediaPolicy;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.UploadAssetApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3.ObjectKeyFactory;
import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3.ObjectStorageProperties;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccessApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadAssetApplicationServiceTest {

    @Mock
    private ProcessingRequestApplication processingRequestApplication;
    @Mock
    private DirectProcessingCompatibilityAdapter compatibilityAdapter;
    @Mock
    private AssetPersistenceService assetPersistenceService;
    @Mock
    private WorkspaceAccessApplication workspaceService;
    @Mock
    private ObjectStorageApplication objectStorageClient;
    @Mock
    private ObjectKeyFactory objectKeyFactory;

    private ObjectStorageProperties objectStorageProperties;

    @BeforeEach
    void setUp() {
        objectStorageProperties = new ObjectStorageProperties();
        objectStorageProperties.setBucket("workspace-media");
    }

    @Test
    void kafkaUploadStoresBinaryThenPersistsTheExistingAsyncIntent() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Workspace", "user-1", false);
        MockMultipartFile file = file();
        StoredObjectReference storedObject = storedObject();
        AssetUploadResponse expected = new AssetUploadResponse(
                UUID.randomUUID(), UUID.randomUUID(), AssetStatus.PROCESSING, workspaceId
        );
        when(processingRequestApplication.usesKafkaRequestMode()).thenReturn(true);
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, workspace.getOwnerId()));
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(assetPersistenceService.persistKafkaRequestUpload(
                any(),
                org.mockito.Mockito.eq("lecture.mp4"),
                org.mockito.Mockito.eq("Lecture"),
                org.mockito.Mockito.eq(workspaceId),
                org.mockito.Mockito.eq(workspace.getOwnerId()),
                org.mockito.Mockito.eq(storedObject)
        )).thenReturn(expected);

        AssetUploadResponse result = service().uploadAsset(workspaceId, file, "Lecture");

        assertThat(result).isEqualTo(expected);
        verify(compatibilityAdapter, never()).upload(any(), any(), any());
    }

    @Test
    void directCompatibilityFailureRetainsBestEffortObjectCleanup() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Workspace", "user-1", false);
        StoredObjectReference storedObject = storedObject();
        when(processingRequestApplication.usesKafkaRequestMode()).thenReturn(false);
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, workspace.getOwnerId()));
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(compatibilityAdapter.upload(any(), any(), any()))
                .thenReturn(new DirectProcessingUploadResult(
                        "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
                ));
        RuntimeException failure = new RuntimeException("persistence failed");
        when(assetPersistenceService.persistDirectUploadResult(
                any(), any(), any(), any(), any(), any()
        )).thenThrow(failure);

        assertThatThrownBy(() -> service().uploadAsset(workspaceId, file(), "Lecture"))
                .isSameAs(failure);
        verify(objectStorageClient).delete(storedObject);
    }

    @Test
    void unsupportedMediaIsRejectedBeforeStoragePersistenceOrProcessingIntent() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Workspace", "user-1", false);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes());
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, workspace.getOwnerId()));

        assertThatThrownBy(() -> service().uploadAsset(workspaceId, file, "Notes"))
                .isInstanceOf(InvalidUploadRequestException.class)
                .hasMessage("Only MP4, MOV, M4V, WebM, and AVI video files are supported");

        verifyNoInteractions(
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                objectStorageClient
        );
    }

    private UploadAssetApplicationService service() {
        return new UploadAssetApplicationService(
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                workspaceService,
                objectStorageClient,
                new SupportedUploadMediaPolicy()
        );
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "lecture.mp4", "video/mp4", mp4Signature());
    }

    private StoredObjectReference storedObject() {
        return new StoredObjectReference("workspace-media", "objects/raw.mp4", 12L, "video/mp4", "etag");
    }

    private byte[] mp4Signature() {
        return new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }
}
