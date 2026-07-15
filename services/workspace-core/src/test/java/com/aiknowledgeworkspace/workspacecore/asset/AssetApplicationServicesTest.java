package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.UploadAssetApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.SupportedUploadMediaPolicy;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingCompatibilityGateway;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingIntegrationException;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3.ObjectKeyFactory;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3.ObjectStorageProperties;
import com.aiknowledgeworkspace.workspacecore.storage.StoreObjectRequest;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence.WorkspaceRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.application.internal.WorkspaceService;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAssetUsagePort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AssetApplicationServicesTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ProcessingRequestApplication processingRequestApplication;

    @Mock
    private DirectProcessingCompatibilityGateway fastApiProcessingClient;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private ObjectStorageClient objectStorageClient;

    private final ObjectKeyFactory objectKeyFactory = new ObjectKeyFactory();
    private final ObjectStorageProperties objectStorageProperties = new ObjectStorageProperties();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @BeforeEach
    void setUp() {
        lenient().when(workspaceService.isOwnedByCurrentUser(any(Workspace.class))).thenReturn(true);
    }

    @Test
    void uploadAssociatesAssetWithResolvedWorkspace() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient
        );

        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms", "user-1", false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                mp4Signature()
        );
        StoredObject storedObject = storedObject(assetId, workspaceId, "lecture.mp4", "video/mp4", 12L);
        AssetUploadResponse persistedResponse = new AssetUploadResponse(
                assetId,
                processingJobId,
                AssetStatus.PROCESSING,
                workspaceId
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(fastApiProcessingClient.upload(any())).thenReturn(new DirectProcessingUploadResult(
                "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
        ));
        when(assetPersistenceService.persistDirectUploadResult(
                any(UUID.class),
                eq("lecture.mp4"),
                eq("Lecture 1"),
                eq(workspace),
                eq(storedObject),
                eq(new DirectProcessingUploadResult(
                        "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
                ))
        )).thenReturn(persistedResponse);

        AssetUploadResponse response = assetService.uploadAsset(workspaceId, file, "Lecture 1");

        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).resolveWorkspaceOrDefault(workspaceId);
        verify(fastApiProcessingClient).upload(any());
        verify(assetPersistenceService, never()).persistKafkaRequestUpload(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        ArgumentCaptor<StoreObjectCommand> storageRequestCaptor = ArgumentCaptor.forClass(StoreObjectCommand.class);
        verify(objectStorageClient).store(storageRequestCaptor.capture());
        assertThat(storageRequestCaptor.getValue().userId()).isEqualTo("user-1");
        assertThat(storageRequestCaptor.getValue().workspaceId()).isEqualTo(workspaceId);
        assertThat(storageRequestCaptor.getValue().originalFilename()).isEqualTo("lecture.mp4");
        assertThat(storageRequestCaptor.getValue().sizeBytes()).isEqualTo(12L);
        assertThat(storageRequestCaptor.getValue().contentType()).isEqualTo("video/mp4");
    }

    @Test
    void uploadUsesDefaultWorkspaceWhenWorkspaceIdIsOmitted() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient
        );

        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Workspace workspace = new Workspace(workspaceId, "Default Workspace", "default-user", true);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                mp4Signature()
        );
        StoredObject storedObject = storedObject(UUID.randomUUID(), workspaceId, "lecture.mp4", "video/mp4", 12L);
        AssetUploadResponse persistedResponse = new AssetUploadResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AssetStatus.PROCESSING,
                workspaceId
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(workspace);
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(fastApiProcessingClient.upload(any())).thenReturn(new DirectProcessingUploadResult(
                "task-2", "video-2", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
        ));
        when(assetPersistenceService.persistDirectUploadResult(
                any(UUID.class),
                eq("lecture.mp4"),
                eq("Lecture 2"),
                eq(workspace),
                eq(storedObject),
                eq(new DirectProcessingUploadResult(
                        "task-2", "video-2", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
                ))
        )).thenReturn(persistedResponse);

        AssetUploadResponse response = assetService.uploadAsset(null, file, "Lecture 2");

        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).resolveWorkspaceOrDefault(null);
    }

    @Test
    void uploadInKafkaRequestModeDoesNotCallFastApiDirectUploadAndPersistsOutboxIntent() {
        when(processingRequestApplication.usesKafkaRequestMode()).thenReturn(true);
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient
        );

        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms", "user-1", false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                mp4Signature()
        );
        StoredObject storedObject = storedObject(assetId, workspaceId, "lecture.mp4", "video/mp4", 12L);
        AssetUploadResponse persistedResponse = new AssetUploadResponse(
                assetId,
                processingJobId,
                AssetStatus.PROCESSING,
                workspaceId
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(assetPersistenceService.persistKafkaRequestUpload(
                any(UUID.class),
                eq("lecture.mp4"),
                eq("Lecture 1"),
                eq(workspace),
                eq(storedObject)
        )).thenReturn(persistedResponse);

        AssetUploadResponse response = assetService.uploadAsset(workspaceId, file, "Lecture 1");

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.processingJobId()).isEqualTo(processingJobId);
        assertThat(response.assetStatus()).isEqualTo(AssetStatus.PROCESSING);
        verifyNoInteractions(fastApiProcessingClient);
        verify(assetPersistenceService, never()).persistDirectUploadResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(assetPersistenceService).persistKafkaRequestUpload(
                any(UUID.class),
                eq("lecture.mp4"),
                eq("Lecture 1"),
                eq(workspace),
                eq(storedObject)
        );
    }

    @Test
    void uploadCleansStoredObjectWhenFastApiUploadFails() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms", "user-1", false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                mp4Signature()
        );
        StoredObject storedObject = storedObject(UUID.randomUUID(), workspaceId, "lecture.mp4", "video/mp4", 12L);

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(fastApiProcessingClient.upload(any()))
                .thenThrow(new DirectProcessingIntegrationException("FastAPI failed", null));

        assertThatThrownBy(() -> assetService.uploadAsset(workspaceId, file, "Lecture 1"))
                .isInstanceOf(DirectProcessingIntegrationException.class)
                .hasMessage("FastAPI failed");

        verify(objectStorageClient).delete(storedObject);
        verify(assetPersistenceService, never()).persistDirectUploadResult(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void uploadCleansStoredObjectWhenDatabasePersistenceFails() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService,
                objectStorageClient
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms", "user-1", false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                mp4Signature()
        );
        StoredObject storedObject = storedObject(UUID.randomUUID(), workspaceId, "lecture.mp4", "video/mp4", 12L);
        RuntimeException persistenceFailure = new RuntimeException("db down");

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(objectStorageClient.store(any(StoreObjectCommand.class))).thenReturn(storedObject);
        when(fastApiProcessingClient.upload(any())).thenReturn(new DirectProcessingUploadResult(
                "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
        ));
        when(assetPersistenceService.persistDirectUploadResult(
                any(UUID.class),
                eq("lecture.mp4"),
                eq("Lecture 1"),
                eq(workspace),
                eq(storedObject),
                eq(new DirectProcessingUploadResult(
                        "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
                ))
        )).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> assetService.uploadAsset(workspaceId, file, "Lecture 1"))
                .isSameAs(persistenceFailure);

        verify(objectStorageClient).delete(storedObject);
    }

    @Test
    void getAssetRejectsAssetWithoutWorkspace() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        Asset assetWithoutWorkspace = asset(assetId, "lecture.mp4", "Lecture", AssetStatus.TRANSCRIPT_READY);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(assetWithoutWorkspace));

        assertThatThrownBy(() -> assetService.getAsset(assetId))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("Asset not found");

        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void getAssetReturnsOwnedAssetWithoutBackfill() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);
        Asset ownedAsset = asset(assetId, "owned.mp4", "Owned Lecture", AssetStatus.SEARCHABLE, workspace, null);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(ownedAsset));

        Asset result = assetService.getAsset(assetId);

        assertThat(result).isSameAs(ownedAsset);
        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void getAssetStatusReturnsLocalStateWhenNoDirectFastApiTaskExists() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);
        Asset asset = asset(assetId, "lecture.mp4", "Lecture", AssetStatus.PROCESSING, workspace, null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                null,
                null,
                ProcessingJobStatus.PENDING,
                "kafka_request_pending"
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));

        AssetStatusResponse response = assetService.getAssetStatus(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.assetStatus()).isEqualTo(AssetStatus.PROCESSING);
        assertThat(response.processingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void getAssetRejectsNonOwnedAssetWithOwnershipSafeNotFound() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-2", false);
        Asset nonOwnedAsset = asset(assetId, "owned.mp4", "Owned Lecture", AssetStatus.SEARCHABLE, workspace, null);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(nonOwnedAsset));
        when(workspaceService.isOwnedByCurrentUser(workspace)).thenReturn(false);

        assertThatThrownBy(() -> assetService.getAsset(assetId))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("Asset not found");

        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void listAssetsUsesCurrentUserFromSessionAuthEntry() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        WorkspaceAssetUsagePort assetWorkspaceUsageService = org.mockito.Mockito.mock(WorkspaceAssetUsagePort.class);
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService realWorkspaceService = new WorkspaceService(
                workspaceRepository,
                assetWorkspaceUsageService,
                workspaceProperties,
                currentUserService
        );
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                realWorkspaceService
        );

        String currentUserId = "session-user";
        Workspace defaultWorkspace = new Workspace(UUID.randomUUID(), "Default Workspace", currentUserId, true);
        Asset asset = asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture 1",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                Instant.parse("2026-04-10T02:00:00Z")
        );

        bindSessionCurrentUser(currentUserService, currentUserId);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId))
                .thenReturn(List.of(defaultWorkspace));
        when(assetRepository.findByWorkspace_Id(defaultWorkspace.getId(), assetListSort()))
                .thenReturn(List.of(asset));

        AssetListResponse response = assetService.listAssets(null, null, null, null);

        assertThat(response.items()).extracting(AssetSummaryResponse::assetId)
                .containsExactly(asset.getId());
    }

    @Test
    void getAssetRejectsNonOwnedAssetWhenCurrentUserComesFromSession() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        WorkspaceAssetUsagePort assetWorkspaceUsageService = org.mockito.Mockito.mock(WorkspaceAssetUsagePort.class);
        WorkspaceService realWorkspaceService = new WorkspaceService(
                workspaceRepository,
                assetWorkspaceUsageService,
                new WorkspaceProperties(),
                currentUserService
        );
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                realWorkspaceService
        );

        Workspace nonOwnedWorkspace = new Workspace(UUID.randomUUID(), "Algorithms", "other-user", false);
        Asset nonOwnedAsset = asset(UUID.randomUUID(), "lecture.mp4", "Lecture 1", AssetStatus.SEARCHABLE, nonOwnedWorkspace, null);

        bindSessionCurrentUser(currentUserService, "session-user");
        when(assetRepository.findById(nonOwnedAsset.getId())).thenReturn(Optional.of(nonOwnedAsset));

        assertThatThrownBy(() -> assetService.getAsset(nonOwnedAsset.getId()))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("Asset not found");
    }

    @Test
    void listAssetsUsesDefaultWorkspaceScopeOnly() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID defaultWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Workspace defaultWorkspace = new Workspace(defaultWorkspaceId, "Default Workspace");
        defaultWorkspace.setDefaultWorkspace(true);
        Asset newestAsset = asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture 1",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                Instant.parse("2026-04-10T02:00:00Z")
        );
        Asset olderAsset = asset(
                UUID.randomUUID(),
                "lecture-older.mp4",
                "Lecture 0",
                AssetStatus.TRANSCRIPT_READY,
                defaultWorkspace,
                Instant.parse("2026-04-10T01:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(defaultWorkspace);
        when(assetRepository.findByWorkspace_Id(defaultWorkspaceId, assetListSort()))
                .thenReturn(List.of(olderAsset, newestAsset));

        AssetListResponse response = assetService.listAssets(null, null, null, null);

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).assetId()).isEqualTo(newestAsset.getId());
        assertThat(response.items().get(0).workspaceId()).isEqualTo(defaultWorkspaceId);
        assertThat(response.items().get(1).assetId()).isEqualTo(olderAsset.getId());
        assertThat(response.items().get(1).workspaceId()).isEqualTo(defaultWorkspaceId);
        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void listAssetsUsesRequestedNonDefaultWorkspace() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Distributed Systems");
        Asset asset = asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture 8",
                AssetStatus.PROCESSING,
                workspace,
                Instant.parse("2026-04-10T05:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(assetRepository.findByWorkspace_Id(workspaceId, assetListSort()))
                .thenReturn(List.of(asset));

        AssetListResponse response = assetService.listAssets(workspaceId, null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).assetId()).isEqualTo(asset.getId());
        assertThat(response.items().get(0).workspaceId()).isEqualTo(workspaceId);
        verify(assetRepository).findByWorkspace_Id(workspaceId, assetListSort());
    }

    @Test
    void listAssetsSupportsExplicitPageAndSize() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms");
        Asset newestAsset = asset(
                UUID.randomUUID(),
                "lecture-3.mp4",
                "Lecture 3",
                AssetStatus.SEARCHABLE,
                workspace,
                Instant.parse("2026-04-10T05:00:00Z")
        );
        Asset middleAsset = asset(
                UUID.randomUUID(),
                "lecture-2.mp4",
                "Lecture 2",
                AssetStatus.TRANSCRIPT_READY,
                workspace,
                Instant.parse("2026-04-10T04:00:00Z")
        );
        Asset oldestAsset = asset(
                UUID.randomUUID(),
                "lecture-1.mp4",
                "Lecture 1",
                AssetStatus.PROCESSING,
                workspace,
                Instant.parse("2026-04-10T03:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(assetRepository.findByWorkspace_Id(workspaceId, assetListSort()))
                .thenReturn(List.of(oldestAsset, middleAsset, newestAsset));

        AssetListResponse response = assetService.listAssets(workspaceId, 1, 1, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).assetId()).isEqualTo(middleAsset.getId());
    }

    @Test
    void listAssetsFiltersByAssetStatusWithinWorkspaceScope() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Systems");
        Asset searchableAsset = asset(
                UUID.randomUUID(),
                "lecture-2.mp4",
                "Lecture 2",
                AssetStatus.SEARCHABLE,
                workspace,
                Instant.parse("2026-04-10T05:00:00Z")
        );
        Asset processingAsset = asset(
                UUID.randomUUID(),
                "lecture-1.mp4",
                "Lecture 1",
                AssetStatus.PROCESSING,
                workspace,
                Instant.parse("2026-04-10T04:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(assetRepository.findByWorkspace_Id(workspaceId, assetListSort()))
                .thenReturn(List.of(processingAsset, searchableAsset));

        AssetListResponse response = assetService.listAssets(workspaceId, null, null, AssetStatus.SEARCHABLE);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting(AssetSummaryResponse::assetId)
                .containsExactly(searchableAsset.getId());
    }

    @Test
    void listAssetsRejectsNegativePage() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        assertThatThrownBy(() -> assetService.listAssets(null, -1, null, null))
                .isInstanceOf(AssetListRequestException.class)
                .hasMessage("page must be greater than or equal to 0");
    }

    @Test
    void listAssetsRejectsNonPositiveSize() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        assertThatThrownBy(() -> assetService.listAssets(null, null, 0, null))
                .isInstanceOf(AssetListRequestException.class)
                .hasMessage("size must be greater than 0");
    }

    @Test
    void listAssetsRejectsSizeAboveMaximum() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        assertThatThrownBy(() -> assetService.listAssets(null, null, 101, null))
                .isInstanceOf(AssetListRequestException.class)
                .hasMessage("size must be less than or equal to 100");
    }

    @Test
    void listAssetsUsesDeterministicOrderingWhenCreatedAtMatches() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID defaultWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Workspace defaultWorkspace = new Workspace(defaultWorkspaceId, "Default Workspace");
        defaultWorkspace.setDefaultWorkspace(true);
        Instant sharedCreatedAt = Instant.parse("2026-04-10T02:00:00Z");
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        Asset workspaceAsset = asset(
                smallerId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                sharedCreatedAt
        );
        Asset largerWorkspaceAsset = asset(
                largerId,
                "lecture-2.mp4",
                "Lecture 2",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                sharedCreatedAt
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(defaultWorkspace);
        when(assetRepository.findByWorkspace_Id(defaultWorkspaceId, assetListSort()))
                .thenReturn(List.of(workspaceAsset, largerWorkspaceAsset));

        AssetListResponse response = assetService.listAssets(null, 0, 20, null);

        assertThat(response.items()).extracting(AssetSummaryResponse::assetId)
                .containsExactly(largerId, smallerId);
    }

    @Test
    void getAssetTranscriptUsesPersistedSnapshotInNormalPath() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 3", AssetStatus.SEARCHABLE, new Workspace(workspaceId, "Algorithms"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-3a",
                "video-3a",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                snapshotRow(assetId, "row-2", "video-3a", 2, "second"),
                snapshotRow(assetId, "row-1", "video-3a", 1, "first")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        List<AssetTranscriptRowResponse> response = assetService.getAssetTranscript(assetId);

        assertThat(response).extracting(AssetTranscriptRowResponse::id)
                .containsExactly("row-1", "row-2");
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void getAssetTranscriptCapturesAndPersistsSnapshotWhenLocalRowsAreMissing() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 3B", AssetStatus.PROCESSING, new Workspace(workspaceId, "Algorithms"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-3b",
                "video-3b",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<DirectProcessingTranscriptRow> upstreamRows = List.of(
                transcriptRow("row-1", "video-3b", 1, "first"),
                transcriptRow("row-2", "video-3b", 2, "second")
        );
        List<AssetTranscriptRowSnapshot> persistedRows = List.of(
                snapshotRow(assetId, "row-1", "video-3b", 1, "first"),
                snapshotRow(assetId, "row-2", "video-3b", 2, "second")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.transcriptRows("video-3b")).thenReturn(upstreamRows);
        List<AssetTranscriptRowInput> transcriptRows = upstreamRows.stream()
                .map(this::toAssetTranscriptRowInput)
                .toList();
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, transcriptRows)).thenReturn(persistedRows);

        List<AssetTranscriptRowResponse> response = assetService.getAssetTranscript(assetId);

        assertThat(response).extracting(AssetTranscriptRowResponse::id)
                .containsExactly("row-1", "row-2");
        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, transcriptRows);
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void transcriptContextReturnsRequestedWindowAroundMatchedRow() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 3", AssetStatus.SEARCHABLE, new Workspace(workspaceId, "Algorithms"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-3",
                "video-3",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                snapshotRow(assetId, "row-3", "video-3", 3, "third"),
                snapshotRow(assetId, "row-1", "video-3", 1, "first"),
                snapshotRow(assetId, "row-4", "video-3", 4, "fourth"),
                snapshotRow(assetId, "row-2", "video-3", 2, "second"),
                snapshotRow(assetId, "row-5", "video-3", 5, "fifth")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        AssetTranscriptContextResponse response = assetService.getAssetTranscriptContext(assetId, "row-3", 1);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.transcriptRowId()).isEqualTo("row-3");
        assertThat(response.hitSegmentIndex()).isEqualTo(3);
        assertThat(response.window()).isEqualTo(1);
        assertThat(response.rows())
                .extracting(AssetTranscriptRowResponse::id)
                .containsExactly("row-2", "row-3", "row-4");
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void transcriptContextUsesFallbackSegmentIdentifierWhenTranscriptRowIdIsMissing() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 4", AssetStatus.TRANSCRIPT_READY, new Workspace(workspaceId, "Systems"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-4",
                "video-4",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                snapshotRow(assetId, null, "video-4", 0, "intro"),
                snapshotRow(assetId, "row-1", "video-4", 1, "detail"),
                snapshotRow(assetId, "row-2", "video-4", 2, "more")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        AssetTranscriptContextResponse response = assetService.getAssetTranscriptContext(assetId, "segment-0", null);

        assertThat(response.transcriptRowId()).isEqualTo("segment-0");
        assertThat(response.hitSegmentIndex()).isEqualTo(0);
        assertThat(response.window()).isEqualTo(2);
        assertThat(response.rows()).hasSize(3);
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void transcriptContextDoesNotMatchSyntheticSegmentIdentifierWhenRealRowIdExists() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 4B", AssetStatus.TRANSCRIPT_READY, new Workspace(workspaceId, "Systems"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-4b",
                "video-4b",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                snapshotRow(assetId, "row-0", "video-4b", 0, "intro"),
                snapshotRow(assetId, "row-1", "video-4b", 1, "detail")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "segment-0", null))
                .isInstanceOf(TranscriptRowNotFoundException.class)
                .hasMessageContaining("Transcript row not found for asset " + assetId + ": segment-0");
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void transcriptContextRejectsInvalidWindow() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(UUID.randomUUID(), "row-1", 0))
                .isInstanceOf(InvalidTranscriptContextWindowException.class)
                .hasMessageContaining("window must be greater than 0");

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(UUID.randomUUID(), "row-1", 6))
                .isInstanceOf(InvalidTranscriptContextWindowException.class)
                .hasMessageContaining("window must be less than or equal to 5");
    }

    @Test
    void transcriptContextRejectsWhenTranscriptProcessingIsNotYetSuccessful() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 5", AssetStatus.PROCESSING, new Workspace(workspaceId, "Databases"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-5",
                "video-5",
                ProcessingJobStatus.RUNNING,
                "running"
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "row-1", 2))
                .isInstanceOf(TranscriptUnavailableException.class)
                .satisfies(exception -> {
                    TranscriptUnavailableException transcriptException = (TranscriptUnavailableException) exception;
                    assertThat(transcriptException.getCode()).isEqualTo("TRANSCRIPT_NOT_READY");
                    assertThat(transcriptException.getMessage())
                            .isEqualTo("Transcript is not ready until processing reaches terminal success");
                });
        verifyNoInteractions(fastApiProcessingClient, assetPersistenceService);
    }

    @Test
    void transcriptContextRejectsWhenTranscriptIsEmptyAndMarksAssetFailed() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 6", AssetStatus.PROCESSING, new Workspace(workspaceId, "Distributed Systems"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-6",
                "video-6",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.transcriptRows("video-6")).thenReturn(List.of());

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "row-1", 2))
                .isInstanceOf(TranscriptUnavailableException.class)
                .satisfies(exception -> {
                    TranscriptUnavailableException transcriptException = (TranscriptUnavailableException) exception;
                    assertThat(transcriptException.getCode()).isEqualTo("TRANSCRIPT_NOT_USABLE");
                    assertThat(transcriptException.getMessage()).isEqualTo("Transcript is empty or unusable for this asset");
                });
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.FAILED);
    }

    @Test
    void transcriptContextCapturesOnlyUsableTranscriptRows() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 7", AssetStatus.PROCESSING, new Workspace(workspaceId, "AI"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-7",
                "video-7",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        DirectProcessingTranscriptRow usableRow = new DirectProcessingTranscriptRow(
                "row-1",
                "video-7",
                1,
                "Useful transcript row",
                "2026-04-12T00:00:00Z"
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.transcriptRows("video-7")).thenReturn(List.of(
                new DirectProcessingTranscriptRow("row-blank", "video-7", 0, "   ", "2026-04-12T00:00:00Z"),
                new DirectProcessingTranscriptRow("row-missing-segment", "video-7", null, "Still bad", "2026-04-12T00:00:01Z"),
                usableRow
        ));
        List<AssetTranscriptRowInput> usableRows = List.of(toAssetTranscriptRowInput(usableRow));
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, usableRows)).thenReturn(List.of(
                snapshotRow(assetId, "row-1", "video-7", 1, "Useful transcript row")
        ));

        AssetTranscriptContextResponse response = assetService.getAssetTranscriptContext(assetId, "row-1", 2);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).id()).isEqualTo("row-1");
        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, usableRows);
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void transcriptContextRejectsUnknownTranscriptRowForAsset() {
        AssetApplicationFixture assetService = new AssetApplicationFixture(
                assetRepository,
                processingRequestApplication,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, "lecture.mp4", "Lecture 5", AssetStatus.SEARCHABLE, new Workspace(workspaceId, "Databases"), null);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-5",
                "video-5",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                snapshotRow(assetId, "row-1", "video-5", 1, "first"),
                snapshotRow(assetId, "row-2", "video-5", 2, "second")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJobView(processingJob)));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "row-404", 2))
                .isInstanceOf(TranscriptRowNotFoundException.class)
                .hasMessageContaining("Transcript row not found for asset " + assetId + ": row-404");
        verifyNoInteractions(fastApiProcessingClient);
    }

    /**
     * Test-only composition fixture for the focused asset application services. Production
     * code uses Spring constructor injection and no longer needs the former AssetService
     * compatibility facade.
     */
    private static final class AssetApplicationFixture {

        private final UploadAssetApplicationService uploadApplicationService;
        private final AssetQueryApplicationService queryApplicationService;

        private AssetApplicationFixture(
                AssetRepository assetRepository,
                ProcessingRequestApplication processingRequestApplication,
                DirectProcessingCompatibilityGateway fastApiProcessingClient,
                AssetPersistenceService assetPersistenceService,
                WorkspaceService workspaceService,
                ObjectStorageClient objectStorageClient
        ) {
            AssetTranscriptQueryService transcriptQueryService = new AssetTranscriptQueryService(
                    assetRepository,
                    assetPersistenceService,
                    workspaceService
            );
            AssetTranscriptSnapshotService transcriptSnapshotService = new AssetTranscriptSnapshotService(
                    assetRepository,
                    assetPersistenceService,
                    (assetId, rows) -> {
                    }
            );
            DirectProcessingCompatibilityAdapter compatibilityAdapter = new DirectProcessingCompatibilityAdapter(
                    fastApiProcessingClient,
                    transcriptQueryService,
                    transcriptSnapshotService
            );
            this.uploadApplicationService = new UploadAssetApplicationService(
                    processingRequestApplication,
                    compatibilityAdapter,
                    assetPersistenceService,
                    workspaceService,
                    objectStorageClient,
                    new SupportedUploadMediaPolicy()
            );
            this.queryApplicationService = new AssetQueryApplicationService(
                    assetRepository,
                    processingRequestApplication,
                    compatibilityAdapter,
                    assetPersistenceService,
                    workspaceService
            );
        }

        private AssetApplicationFixture(
                AssetRepository assetRepository,
                ProcessingRequestApplication processingRequestApplication,
                DirectProcessingCompatibilityGateway fastApiProcessingClient,
                AssetPersistenceService assetPersistenceService,
                WorkspaceService workspaceService
        ) {
            this(
                    assetRepository,
                    processingRequestApplication,
                    fastApiProcessingClient,
                    assetPersistenceService,
                    workspaceService,
                    new ObjectStorageClient() {
                        @Override
                        public StoredObject store(StoreObjectRequest request) {
                            throw new IllegalStateException("Object storage client is not configured");
                        }

                        @Override
                        public void delete(String bucket, String objectKey) {
                        }
                    }
            );
        }

        private AssetUploadResponse uploadAsset(UUID workspaceId, MultipartFile file, String requestedTitle) {
            return uploadApplicationService.uploadAsset(workspaceId, file, requestedTitle);
        }

        private Asset getAsset(UUID assetId) {
            return queryApplicationService.getAsset(assetId);
        }

        private AssetListResponse listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus status) {
            return queryApplicationService.listAssets(workspaceId, page, size, status);
        }

        private AssetStatusResponse getAssetStatus(UUID assetId) {
            return queryApplicationService.getAssetStatus(assetId);
        }

        private List<AssetTranscriptRowResponse> getAssetTranscript(UUID assetId) {
            return queryApplicationService.getAssetTranscript(assetId);
        }

        private AssetTranscriptContextResponse getAssetTranscriptContext(
                UUID assetId,
                String transcriptRowId,
                Integer window
        ) {
            return queryApplicationService.getAssetTranscriptContext(assetId, transcriptRowId, window);
        }
    }

    private Asset asset(UUID assetId, String originalFilename, String title, AssetStatus status) {
        Asset asset = asset(
                assetId,
                originalFilename,
                title,
                status,
                new Workspace(UUID.randomUUID(), "Corrupted Workspace Placeholder"),
                null
        );
        ReflectionTestUtils.setField(asset, "workspace", null);
        return asset;
    }

    private Asset asset(
            UUID assetId,
            String originalFilename,
            String title,
            AssetStatus status,
            Workspace workspace,
            Instant createdAt
    ) {
        Asset asset = new Asset(originalFilename, title, status, workspace);
        ReflectionTestUtils.setField(asset, "id", assetId);
        if (createdAt != null) {
            ReflectionTestUtils.setField(asset, "createdAt", createdAt);
        }
        return asset;
    }

    private Sort assetListSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }

    private DirectProcessingTranscriptRow transcriptRow(String id, String videoId, int segmentIndex, String text) {
        return new DirectProcessingTranscriptRow(
                id,
                videoId,
                segmentIndex,
                text,
                "2026-04-12T00:00:00Z"
        );
    }

    private AssetTranscriptRowInput toAssetTranscriptRowInput(DirectProcessingTranscriptRow transcriptRow) {
        return new AssetTranscriptRowInput(
                transcriptRow.id(),
                transcriptRow.videoId(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt()
        );
    }

    private ProcessingJobView processingJobView(ProcessingJob job) {
        return new ProcessingJobView(
                job.getId(),
                job.getAssetId(),
                job.getFastapiTaskId(),
                job.getFastapiVideoId(),
                job.getProcessingJobStatus(),
                job.getRawUpstreamTaskState()
        );
    }

    private StoredObject storedObject(
            UUID assetId,
            UUID workspaceId,
            String filename,
            String contentType,
            long sizeBytes
    ) {
        return new StoredObject(
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/%s".formatted(workspaceId, assetId, filename),
                sizeBytes,
                contentType,
                "\"etag-1\""
        );
    }

    private static byte[] mp4Signature() {
        return new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }

    private AssetTranscriptRowSnapshot snapshotRow(
            UUID assetId,
            String id,
            String videoId,
            Integer segmentIndex,
            String text
    ) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                id,
                videoId,
                segmentIndex,
                text,
                "2026-04-12T00:00:00Z"
        );
    }

    private void bindSessionCurrentUser(CurrentUserService currentUserService, String currentUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        currentUserService.establishCurrentUser(session, currentUserId);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
