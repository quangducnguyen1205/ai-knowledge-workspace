package com.aiknowledgeworkspace.workspacecore.processing.application;

import java.util.Optional;
import java.util.UUID;

public interface ProcessingRequestApplication {
    boolean usesKafkaRequestMode();

    Optional<ProcessingJobView> findByAssetId(UUID assetId);

    ProcessingJobView createDirectJob(DirectProcessingJobCommand command);

    ProcessingJobView createKafkaJobAndRequest(KafkaProcessingRequestCommand command);

    ProcessingJobView updateJob(ProcessingJobUpdateCommand command);

    void deleteForAsset(UUID assetId);
}
