package com.aiknowledgeworkspace.workspacecore.processing.api;

import java.util.Optional;
import java.util.UUID;

public interface ProcessingRequestUseCase {
    Optional<ProcessingJobView> findByAssetId(UUID assetId);

    ProcessingJobView createKafkaJobAndRequest(ProcessingRequestCommand command);

    ProcessingJobView createYouTubeKafkaJobAndRequest(YouTubeProcessingRequestCommand command);

    ProcessingJobView retryKafkaJobAndRequest(ProcessingRequestCommand command);

    ProcessingJobView retryYouTubeKafkaJobAndRequest(YouTubeProcessingRequestCommand command);

    void deleteForAsset(UUID assetId);
}
