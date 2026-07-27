package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class YouTubeAssetCreationTransactionTest {

    private final AssetStore assetStore = mock(AssetStore.class);
    private final ProcessingRequestUseCase processingRequests = mock(ProcessingRequestUseCase.class);
    private final YouTubeAssetCreationTransaction transaction =
            new YouTubeAssetCreationTransaction(assetStore, processingRequests);

    @Test
    void persistsYoutubeProductTruthBeforeCreatingOnlyTheV2ProcessingIntent() {
        UUID workspaceId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        WorkspaceAccess workspace = new WorkspaceAccess(workspaceId, "owner-1");
        when(assetStore.saveYoutube(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(processingRequests.createYouTubeKafkaJobAndRequest(any()))
                .thenAnswer(invocation -> {
                    YouTubeProcessingRequestCommand command = invocation.getArgument(0);
                    return new ProcessingJobView(
                            processingJobId,
                            command.assetId(),
                            ProcessingJobStatus.PENDING,
                            "processing_request_pending"
                    );
                });

        var result = transaction.persist(workspace, "abc_DEF-123", "Lecture");

        ArgumentCaptor<Asset> asset = ArgumentCaptor.forClass(Asset.class);
        ArgumentCaptor<YouTubeProcessingRequestCommand> command =
                ArgumentCaptor.forClass(YouTubeProcessingRequestCommand.class);
        var order = inOrder(assetStore, processingRequests);
        order.verify(assetStore).saveYoutube(asset.capture());
        order.verify(processingRequests).createYouTubeKafkaJobAndRequest(command.capture());
        assertThat(asset.getValue().getSourceType()).isEqualTo(AssetSourceType.YOUTUBE);
        assertThat(asset.getValue().getYoutubeVideoId()).isEqualTo("abc_DEF-123");
        assertThat(asset.getValue().getStatus()).isEqualTo(AssetStatus.PROCESSING);
        assertThat(asset.getValue().getStorageBucket()).isNull();
        assertThat(command.getValue()).isEqualTo(new YouTubeProcessingRequestCommand(
                asset.getValue().getId(), workspaceId, "owner-1", "abc_DEF-123"
        ));
        assertThat(result.processingJobId()).isEqualTo(processingJobId);
    }
}
