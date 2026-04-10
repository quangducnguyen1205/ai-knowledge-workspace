package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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
        when(workspaceService.ensureDefaultWorkspace()).thenReturn(defaultWorkspace);
        when(assetPersistenceService.updateAssetWorkspace(legacyAsset, defaultWorkspace)).thenReturn(updatedAsset);

        Asset result = assetService.getAsset(assetId);

        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        verify(workspaceService).ensureDefaultWorkspace();
        verify(assetPersistenceService).updateAssetWorkspace(legacyAsset, defaultWorkspace);
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
        when(workspaceService.getDefaultWorkspaceId()).thenReturn(defaultWorkspaceId);
        when(assetRepository.findByWorkspace_Id(defaultWorkspaceId, assetListSort()))
                .thenReturn(List.of(workspaceAsset));
        when(assetRepository.findByWorkspaceIsNull(assetListSort()))
                .thenReturn(List.of(legacyAsset));
        when(assetPersistenceService.updateAssetWorkspace(legacyAsset, defaultWorkspace))
                .thenReturn(backfilledLegacyAsset);

        List<AssetSummaryResponse> responses = assetService.listAssets(null);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).assetId()).isEqualTo(workspaceAsset.getId());
        assertThat(responses.get(0).workspaceId()).isEqualTo(defaultWorkspaceId);
        assertThat(responses.get(1).assetId()).isEqualTo(legacyAsset.getId());
        assertThat(responses.get(1).workspaceId()).isEqualTo(defaultWorkspaceId);
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
        when(workspaceService.getDefaultWorkspaceId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(assetRepository.findByWorkspace_Id(workspaceId, assetListSort()))
                .thenReturn(List.of(asset));

        List<AssetSummaryResponse> responses = assetService.listAssets(workspaceId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).assetId()).isEqualTo(asset.getId());
        assertThat(responses.get(0).workspaceId()).isEqualTo(workspaceId);
        verify(assetRepository).findByWorkspace_Id(workspaceId, assetListSort());
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
                Sort.Order.asc("id")
        );
    }
}
