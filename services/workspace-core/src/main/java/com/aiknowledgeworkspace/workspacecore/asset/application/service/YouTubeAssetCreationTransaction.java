package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class YouTubeAssetCreationTransaction {

    private final AssetStore assetStore;
    private final ProcessingRequestUseCase processingRequests;

    YouTubeAssetCreationTransaction(AssetStore assetStore, ProcessingRequestUseCase processingRequests) {
        this.assetStore = assetStore;
        this.processingRequests = processingRequests;
    }

    @Transactional
    AssetProcessingResult persist(WorkspaceAccess workspace, String youtubeVideoId, String title) {
        Asset asset = assetStore.saveYoutube(Asset.youtube(
                UUID.randomUUID(),
                youtubeVideoId,
                title,
                AssetStatus.PROCESSING,
                workspace.workspaceId()
        ));
        ProcessingJobView processingJob = processingRequests.createYouTubeKafkaJobAndRequest(
                new YouTubeProcessingRequestCommand(
                        asset.getId(),
                        workspace.workspaceId(),
                        workspace.ownerId(),
                        youtubeVideoId
                )
        );
        return new AssetProcessingResult(
                asset.getId(),
                processingJob.id(),
                asset.getStatus(),
                asset.getWorkspaceId(),
                asset.getSourceType(),
                asset.getYoutubeVideoId()
        );
    }
}
