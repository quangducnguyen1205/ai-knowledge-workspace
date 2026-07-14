package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTaskState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.internal.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetQueryApplicationServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private ProcessingRequestApplication processingRequestApplication;
    @Mock
    private DirectProcessingCompatibilityAdapter compatibilityAdapter;
    @Mock
    private AssetPersistenceService assetPersistenceService;
    @Mock
    private WorkspaceService workspaceService;

    @Test
    void localStatusProjectionDoesNotCallCompatibilityRuntimeWithoutATaskId() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        ProcessingJobView job = new ProcessingJobView(
                UUID.randomUUID(), assetId, null, null, ProcessingJobStatus.PENDING, null
        );
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceService.isOwnedByCurrentUser(asset.getWorkspace())).thenReturn(true);
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(job));

        AssetStatusResponse result = service().getAssetStatus(assetId);

        assertThat(result.processingJobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        verify(compatibilityAdapter, never()).taskState(org.mockito.Mockito.any());
    }

    @Test
    void directStatusRefreshDelegatesPersistenceAfterCompatibilityRead() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        ProcessingJobView job = new ProcessingJobView(
                UUID.randomUUID(), assetId, "task-1", "video-1", ProcessingJobStatus.RUNNING, "running"
        );
        DirectProcessingTaskState taskState = new DirectProcessingTaskState(
                "success", ProcessingJobStatus.SUCCEEDED, AssetStatus.PROCESSING
        );
        AssetStatusResponse expected = new AssetStatusResponse(
                assetId, job.id(), AssetStatus.PROCESSING, ProcessingJobStatus.SUCCEEDED
        );
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceService.isOwnedByCurrentUser(asset.getWorkspace())).thenReturn(true);
        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(job));
        when(compatibilityAdapter.taskState("task-1")).thenReturn(taskState);
        when(assetPersistenceService.refreshAssetStatus(
                asset,
                job,
                "success",
                ProcessingJobStatus.SUCCEEDED,
                AssetStatus.PROCESSING
        )).thenReturn(expected);

        assertThat(service().getAssetStatus(assetId)).isEqualTo(expected);
    }

    private AssetQueryApplicationService service() {
        return new AssetQueryApplicationService(
                assetRepository,
                processingRequestApplication,
                compatibilityAdapter,
                assetPersistenceService,
                workspaceService
        );
    }

    private Asset asset(UUID assetId, AssetStatus status) {
        Asset asset = new Asset(
                "lecture.mp4", "Lecture", status, new Workspace(UUID.randomUUID(), "Workspace")
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }
}
