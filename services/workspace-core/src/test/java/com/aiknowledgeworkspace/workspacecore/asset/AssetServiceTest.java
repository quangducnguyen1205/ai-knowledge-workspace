package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private FastApiProcessingClient fastApiProcessingClient;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Mock
    private WorkspaceService workspaceService;

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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                "video-bytes".getBytes(StandardCharsets.UTF_8)
        );
        FastApiUploadResponse upstreamResponse = new FastApiUploadResponse("task-1", "pending", "video-1");
        AssetUploadResponse persistedResponse = new AssetUploadResponse(
                assetId,
                processingJobId,
                AssetStatus.PROCESSING,
                workspaceId
        );

        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);
        when(fastApiProcessingClient.uploadVideo(any(Resource.class), eq("lecture.mp4"), eq("Lecture 1")))
                .thenReturn(upstreamResponse);
        when(assetPersistenceService.persistUploadResult(
                eq("lecture.mp4"),
                eq("Lecture 1"),
                eq(AssetStatus.PROCESSING),
                eq(ProcessingJobStatus.PENDING),
                eq(workspace),
                eq(upstreamResponse)
        )).thenReturn(persistedResponse);

        AssetUploadResponse response = assetService.uploadAsset(workspaceId, file, "Lecture 1");

        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).resolveWorkspaceOrDefault(workspaceId);
    }

    @Test
    void uploadUsesDefaultWorkspaceWhenWorkspaceIdIsOmitted() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Workspace workspace = new Workspace(workspaceId, "Default Workspace");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                "video-bytes".getBytes(StandardCharsets.UTF_8)
        );
        FastApiUploadResponse upstreamResponse = new FastApiUploadResponse("task-2", "pending", "video-2");
        AssetUploadResponse persistedResponse = new AssetUploadResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AssetStatus.PROCESSING,
                workspaceId
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(workspace);
        when(fastApiProcessingClient.uploadVideo(any(Resource.class), eq("lecture.mp4"), eq("Lecture 2")))
                .thenReturn(upstreamResponse);
        when(assetPersistenceService.persistUploadResult(
                eq("lecture.mp4"),
                eq("Lecture 2"),
                eq(AssetStatus.PROCESSING),
                eq(ProcessingJobStatus.PENDING),
                eq(workspace),
                eq(upstreamResponse)
        )).thenReturn(persistedResponse);

        AssetUploadResponse response = assetService.uploadAsset(null, file, "Lecture 2");

        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).resolveWorkspaceOrDefault(null);
    }

    @Test
    void getAssetBackfillsDefaultWorkspaceForLegacyAsset() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Asset legacyAsset = asset(assetId, "legacy.mp4", "Legacy Lecture", AssetStatus.TRANSCRIPT_READY);
        Asset updatedAsset = asset(assetId, "legacy.mp4", "Legacy Lecture", AssetStatus.TRANSCRIPT_READY);
        Workspace defaultWorkspace = new Workspace(workspaceId, "Default Workspace");
        updatedAsset.setWorkspace(defaultWorkspace);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(legacyAsset));
        when(workspaceService.canAccessLegacyNullWorkspaceAssets()).thenReturn(true);
        when(workspaceService.ensureDefaultWorkspace()).thenReturn(defaultWorkspace);
        when(assetPersistenceService.updateAssetWorkspace(legacyAsset, defaultWorkspace)).thenReturn(updatedAsset);

        Asset result = assetService.getAsset(assetId);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).ensureDefaultWorkspace();
        verify(assetPersistenceService).updateAssetWorkspace(legacyAsset, defaultWorkspace);
    }

    @Test
    void getAssetReturnsOwnedAssetWithoutBackfill() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
    void getAssetRejectsNonOwnedAssetWithOwnershipSafeNotFound() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
    void getAssetRejectsLegacyNullWorkspaceAssetForNonDefaultUser() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID assetId = UUID.randomUUID();
        Asset legacyAsset = asset(assetId, "legacy.mp4", "Legacy Lecture", AssetStatus.TRANSCRIPT_READY);

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(legacyAsset));
        when(workspaceService.canAccessLegacyNullWorkspaceAssets()).thenReturn(false);

        assertThatThrownBy(() -> assetService.getAsset(assetId))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("Asset not found");

        verify(workspaceService, never()).ensureDefaultWorkspace();
        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void listAssetsUsesCurrentUserFromSessionAuthEntry() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceRepository workspaceRepository = org.mockito.Mockito.mock(WorkspaceRepository.class);
        AssetRepository workspaceAssetRepository = org.mockito.Mockito.mock(AssetRepository.class);
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService realWorkspaceService = new WorkspaceService(
                workspaceRepository,
                workspaceAssetRepository,
                workspaceProperties,
                currentUserService
        );
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(workspaceRepository.findByOwnerIdAndDefaultWorkspaceTrue(currentUserId))
                .thenReturn(Optional.of(defaultWorkspace));
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
        AssetRepository workspaceAssetRepository = org.mockito.Mockito.mock(AssetRepository.class);
        WorkspaceService realWorkspaceService = new WorkspaceService(
                workspaceRepository,
                workspaceAssetRepository,
                new WorkspaceProperties(),
                currentUserService
        );
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
    void listAssetsUsesDefaultWorkspaceAndBackfillsLegacyAssets() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID defaultWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Workspace defaultWorkspace = new Workspace(defaultWorkspaceId, "Default Workspace");
        defaultWorkspace.setDefaultWorkspace(true);
        Asset workspaceAsset = asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture 1",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                Instant.parse("2026-04-10T02:00:00Z")
        );
        Asset legacyAsset = asset(
                UUID.randomUUID(),
                "legacy.mp4",
                "Legacy Lecture",
                AssetStatus.TRANSCRIPT_READY,
                null,
                Instant.parse("2026-04-10T01:00:00Z")
        );
        Asset backfilledLegacyAsset = asset(
                legacyAsset.getId(),
                "legacy.mp4",
                "Legacy Lecture",
                AssetStatus.TRANSCRIPT_READY,
                defaultWorkspace,
                Instant.parse("2026-04-10T01:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(defaultWorkspace);
        when(workspaceService.shouldIncludeLegacyNullWorkspaceAssets(defaultWorkspace)).thenReturn(true);
        when(assetRepository.findByWorkspace_Id(defaultWorkspaceId, assetListSort()))
                .thenReturn(List.of(workspaceAsset));
        when(assetRepository.findByWorkspaceIsNull(assetListSort()))
                .thenReturn(List.of(legacyAsset));
        when(assetPersistenceService.updateAssetWorkspace(legacyAsset, defaultWorkspace))
                .thenReturn(backfilledLegacyAsset);

        AssetListResponse response = assetService.listAssets(null, null, null, null);

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).assetId()).isEqualTo(workspaceAsset.getId());
        assertThat(response.items().get(0).workspaceId()).isEqualTo(defaultWorkspaceId);
        assertThat(response.items().get(1).assetId()).isEqualTo(legacyAsset.getId());
        assertThat(response.items().get(1).workspaceId()).isEqualTo(defaultWorkspaceId);
        verify(assetPersistenceService).updateAssetWorkspace(legacyAsset, defaultWorkspace);
    }

    @Test
    void listAssetsUsesRequestedNonDefaultWorkspace() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        Asset legacyAsset = asset(
                largerId,
                "legacy.mp4",
                "Legacy",
                AssetStatus.SEARCHABLE,
                null,
                sharedCreatedAt
        );
        Asset backfilledLegacyAsset = asset(
                largerId,
                "legacy.mp4",
                "Legacy",
                AssetStatus.SEARCHABLE,
                defaultWorkspace,
                sharedCreatedAt
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(defaultWorkspace);
        when(workspaceService.shouldIncludeLegacyNullWorkspaceAssets(defaultWorkspace)).thenReturn(true);
        when(assetRepository.findByWorkspace_Id(defaultWorkspaceId, assetListSort()))
                .thenReturn(List.of(workspaceAsset));
        when(assetRepository.findByWorkspaceIsNull(assetListSort()))
                .thenReturn(List.of(legacyAsset));
        when(assetPersistenceService.updateAssetWorkspace(legacyAsset, defaultWorkspace))
                .thenReturn(backfilledLegacyAsset);

        AssetListResponse response = assetService.listAssets(null, 0, 20, null);

        assertThat(response.items()).extracting(AssetSummaryResponse::assetId)
                .containsExactly(largerId, smallerId);
    }

    @Test
    void listAssetsDoesNotIncludeLegacyNullWorkspaceAssetsForNonDefaultUserDefaultWorkspace() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
                fastApiProcessingClient,
                assetPersistenceService,
                workspaceService
        );

        UUID workspaceId = UUID.randomUUID();
        Workspace userDefaultWorkspace = new Workspace(workspaceId, "Default Workspace");
        userDefaultWorkspace.setDefaultWorkspace(true);
        userDefaultWorkspace.setOwnerId("user-2");
        Asset ownedAsset = asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture 2",
                AssetStatus.SEARCHABLE,
                userDefaultWorkspace,
                Instant.parse("2026-04-10T02:00:00Z")
        );

        when(workspaceService.resolveWorkspaceOrDefault(null)).thenReturn(userDefaultWorkspace);
        when(workspaceService.shouldIncludeLegacyNullWorkspaceAssets(userDefaultWorkspace)).thenReturn(false);
        when(assetRepository.findByWorkspace_Id(workspaceId, assetListSort()))
                .thenReturn(List.of(ownedAsset));

        AssetListResponse response = assetService.listAssets(null, null, null, null);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).extracting(AssetSummaryResponse::assetId)
                .containsExactly(ownedAsset.getId());
        verify(assetRepository, never()).findByWorkspaceIsNull(assetListSort());
        verify(assetPersistenceService, never()).updateAssetWorkspace(any(), any());
    }

    @Test
    void getAssetTranscriptUsesPersistedSnapshotInNormalPath() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        List<AssetTranscriptRowResponse> response = assetService.getAssetTranscript(assetId);

        assertThat(response).extracting(AssetTranscriptRowResponse::id)
                .containsExactly("row-2", "row-1");
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void getAssetTranscriptCapturesAndPersistsSnapshotWhenLocalRowsAreMissing() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        List<FastApiTranscriptRowResponse> upstreamRows = List.of(
                transcriptRow("row-1", "video-3b", 1, "first"),
                transcriptRow("row-2", "video-3b", 2, "second")
        );
        List<AssetTranscriptRowSnapshot> persistedRows = List.of(
                snapshotRow(assetId, "row-1", "video-3b", 1, "first"),
                snapshotRow(assetId, "row-2", "video-3b", 2, "second")
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.getTranscript("video-3b")).thenReturn(upstreamRows);
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, upstreamRows)).thenReturn(persistedRows);

        List<AssetTranscriptRowResponse> response = assetService.getAssetTranscript(assetId);

        assertThat(response).extracting(AssetTranscriptRowResponse::id)
                .containsExactly("row-1", "row-2");
        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, upstreamRows);
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void transcriptContextReturnsRequestedWindowAroundMatchedRow() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "segment-0", null))
                .isInstanceOf(TranscriptRowNotFoundException.class)
                .hasMessageContaining("Transcript row not found for asset " + assetId + ": segment-0");
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verifyNoInteractions(fastApiProcessingClient);
    }

    @Test
    void transcriptContextRejectsInvalidWindow() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));

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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.getTranscript("video-6")).thenReturn(List.of());

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
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        FastApiTranscriptRowResponse usableRow = new FastApiTranscriptRowResponse(
                "row-1",
                "video-7",
                1,
                "Useful transcript row",
                "2026-04-12T00:00:00Z"
        );

        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.getTranscript("video-7")).thenReturn(List.of(
                new FastApiTranscriptRowResponse("row-blank", "video-7", 0, "   ", "2026-04-12T00:00:00Z"),
                new FastApiTranscriptRowResponse("row-missing-segment", "video-7", null, "Still bad", "2026-04-12T00:00:01Z"),
                usableRow
        ));
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, List.of(usableRow))).thenReturn(List.of(
                snapshotRow(assetId, "row-1", "video-7", 1, "Useful transcript row")
        ));

        AssetTranscriptContextResponse response = assetService.getAssetTranscriptContext(assetId, "row-1", 2);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).id()).isEqualTo("row-1");
        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, List.of(usableRow));
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void transcriptContextRejectsUnknownTranscriptRowForAsset() {
        AssetService assetService = new AssetService(
                assetRepository,
                processingJobRepository,
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
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(transcriptRows);

        assertThatThrownBy(() -> assetService.getAssetTranscriptContext(assetId, "row-404", 2))
                .isInstanceOf(TranscriptRowNotFoundException.class)
                .hasMessageContaining("Transcript row not found for asset " + assetId + ": row-404");
        verifyNoInteractions(fastApiProcessingClient);
    }

    private Asset asset(UUID assetId, String originalFilename, String title, AssetStatus status) {
        return asset(assetId, originalFilename, title, status, null, null);
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

    private FastApiTranscriptRowResponse transcriptRow(String id, String videoId, int segmentIndex, String text) {
        return new FastApiTranscriptRowResponse(
                id,
                videoId,
                segmentIndex,
                text,
                "2026-04-12T00:00:00Z"
        );
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
