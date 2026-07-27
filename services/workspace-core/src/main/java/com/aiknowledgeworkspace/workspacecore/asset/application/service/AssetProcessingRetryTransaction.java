package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetProcessingRetryNotAllowedException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetProcessingRetryTransaction {

    private final AssetStore assetStore;
    private final ProcessingRequestUseCase processingRequests;

    AssetProcessingRetryTransaction(AssetStore assetStore, ProcessingRequestUseCase processingRequests) {
        this.assetStore = assetStore;
        this.processingRequests = processingRequests;
    }

    @Transactional
    AssetProcessingResult retry(UUID assetId, UUID authorizedWorkspaceId, String ownerId) {
        Asset asset = assetStore.findByIdForUpdate(assetId).orElseThrow(AssetNotFoundException::new);
        if (!asset.getWorkspaceId().equals(authorizedWorkspaceId)) {
            throw new AssetNotFoundException();
        }
        if (asset.getStatus() != AssetStatus.FAILED) {
            throw new AssetProcessingRetryNotAllowedException();
        }

        asset.setStatus(AssetStatus.PROCESSING);
        assetStore.save(asset);
        ProcessingJobView job = switch (asset.getSourceType()) {
            case UPLOAD -> processingRequests.retryKafkaJobAndRequest(new ProcessingRequestCommand(
                    asset.getId(),
                    asset.getWorkspaceId(),
                    ownerId,
                    asset.getStorageBucket(),
                    asset.getObjectKey(),
                    asset.getOriginalFilename(),
                    asset.getContentType(),
                    asset.getSizeBytes()
            ));
            case YOUTUBE -> processingRequests.retryYouTubeKafkaJobAndRequest(
                    new YouTubeProcessingRequestCommand(
                            asset.getId(),
                            asset.getWorkspaceId(),
                            ownerId,
                            asset.getYoutubeVideoId()
                    )
            );
        };
        return new AssetProcessingResult(
                asset.getId(),
                job.id(),
                asset.getStatus(),
                asset.getWorkspaceId(),
                asset.getSourceType(),
                asset.getYoutubeVideoId()
        );
    }
}
