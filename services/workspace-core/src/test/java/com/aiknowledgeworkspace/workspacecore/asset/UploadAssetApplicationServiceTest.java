package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectKeyFactory;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageProperties;
import com.aiknowledgeworkspace.workspacecore.storage.StoreObjectRequest;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
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
    private WorkspaceService workspaceService;
    @Mock
    private ObjectStorageClient objectStorageClient;
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
        StoredObject storedObject = storedObject();
        AssetUploadResponse expected = new AssetUploadResponse(
                UUID.randomUUID(), UUID.randomUUID(), AssetStatus.PROCESSING, workspaceId
        );
        when(processingRequestApplication.usesKafkaRequestMode()).thenReturn(true);
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectKeyFactory.rawMediaKey(any(), any(), any(), any())).thenReturn("objects/raw.mp4");
        when(objectStorageClient.store(any(StoreObjectRequest.class))).thenReturn(storedObject);
        when(assetPersistenceService.persistKafkaRequestUpload(
                any(),
                org.mockito.Mockito.eq("lecture.mp4"),
                org.mockito.Mockito.eq("Lecture"),
                org.mockito.Mockito.eq(workspace),
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
        StoredObject storedObject = storedObject();
        when(processingRequestApplication.usesKafkaRequestMode()).thenReturn(false);
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectKeyFactory.rawMediaKey(any(), any(), any(), any())).thenReturn("objects/raw.mp4");
        when(objectStorageClient.store(any(StoreObjectRequest.class))).thenReturn(storedObject);
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
        verify(objectStorageClient).delete(storedObject.bucket(), storedObject.objectKey());
    }

    private UploadAssetApplicationService service() {
        return new UploadAssetApplicationService(
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                workspaceService,
                objectStorageClient,
                objectKeyFactory,
                objectStorageProperties
        );
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "lecture.mp4", "video/mp4", "video".getBytes());
    }

    private StoredObject storedObject() {
        return new StoredObject("workspace-media", "objects/raw.mp4", 5L, "video/mp4", "etag");
    }
}
