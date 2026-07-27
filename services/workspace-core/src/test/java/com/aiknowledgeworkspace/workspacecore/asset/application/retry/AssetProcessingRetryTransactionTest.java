package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetProcessingRetryNotAllowedException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetProcessingRetryTransactionTest {

    private final AssetStore assetStore = mock(AssetStore.class);
    private final ProcessingRequestUseCase processingRequests = mock(ProcessingRequestUseCase.class);
    private final AssetProcessingRetryTransaction transaction =
            new AssetProcessingRetryTransaction(assetStore, processingRequests);

    @Test
    void failedUploadReturnsToProcessingAndPublishesAFreshV1RequestWithoutChangingStorageIdentity() {
        UUID workspaceId = UUID.randomUUID();
        Asset asset = uploadedAsset(workspaceId, AssetStatus.FAILED);
        UUID jobId = UUID.randomUUID();
        when(assetStore.findByIdForUpdate(asset.getId())).thenReturn(Optional.of(asset));
        when(processingRequests.retryKafkaJobAndRequest(any())).thenReturn(new ProcessingJobView(
                jobId, asset.getId(), ProcessingJobStatus.PENDING, "processing_request_pending"
        ));

        var result = transaction.retry(asset.getId(), workspaceId, "owner-1");

        ArgumentCaptor<ProcessingRequestCommand> command = ArgumentCaptor.forClass(ProcessingRequestCommand.class);
        verify(processingRequests).retryKafkaJobAndRequest(command.capture());
        assertThat(command.getValue()).isEqualTo(new ProcessingRequestCommand(
                asset.getId(),
                workspaceId,
                "owner-1",
                "workspace-media",
                "objects/lecture.mp4",
                "lecture.mp4",
                "video/mp4",
                42L
        ));
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PROCESSING);
        assertThat(result.sourceType()).isEqualTo(AssetSourceType.UPLOAD);
        assertThat(result.processingJobId()).isEqualTo(jobId);
    }

    @Test
    void failedYoutubeReturnsToProcessingAndPublishesAFreshV2RequestWithoutChangingIdentity() {
        UUID workspaceId = UUID.randomUUID();
        Asset asset = Asset.youtube(
                UUID.randomUUID(), "abc_DEF-123", "Lecture", AssetStatus.FAILED, workspaceId
        );
        UUID jobId = UUID.randomUUID();
        when(assetStore.findByIdForUpdate(asset.getId())).thenReturn(Optional.of(asset));
        when(processingRequests.retryYouTubeKafkaJobAndRequest(any())).thenReturn(new ProcessingJobView(
                jobId, asset.getId(), ProcessingJobStatus.PENDING, "processing_request_pending"
        ));

        var result = transaction.retry(asset.getId(), workspaceId, "owner-1");

        verify(processingRequests).retryYouTubeKafkaJobAndRequest(new YouTubeProcessingRequestCommand(
                asset.getId(), workspaceId, "owner-1", "abc_DEF-123"
        ));
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.PROCESSING);
        assertThat(result.youtubeVideoId()).isEqualTo("abc_DEF-123");
    }

    @Test
    void nonFailedAssetCannotBeRetried() {
        UUID workspaceId = UUID.randomUUID();
        Asset asset = uploadedAsset(workspaceId, AssetStatus.PROCESSING);
        when(assetStore.findByIdForUpdate(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> transaction.retry(asset.getId(), workspaceId, "owner-1"))
                .isInstanceOf(AssetProcessingRetryNotAllowedException.class);

        verifyNoInteractions(processingRequests);
    }

    @Test
    void workspaceMismatchRemainsNotFound() {
        Asset asset = uploadedAsset(UUID.randomUUID(), AssetStatus.FAILED);
        when(assetStore.findByIdForUpdate(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> transaction.retry(asset.getId(), UUID.randomUUID(), "owner-1"))
                .isInstanceOf(AssetNotFoundException.class);

        verifyNoInteractions(processingRequests);
    }

    private Asset uploadedAsset(UUID workspaceId, AssetStatus status) {
        return Asset.uploaded(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture",
                status,
                workspaceId,
                "workspace-media",
                "objects/lecture.mp4",
                "video/mp4",
                42L,
                "etag-1"
        );
    }
}
