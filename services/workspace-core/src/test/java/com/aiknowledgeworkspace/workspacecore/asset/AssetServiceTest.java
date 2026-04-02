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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

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
}
