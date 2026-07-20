package com.aiknowledgeworkspace.workspacecore.processing.application.port.out;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobStore {

    Optional<ProcessingJob> findJobById(UUID jobId);

    Optional<ProcessingJob> findByAssetId(UUID assetId);

    Optional<ProcessingJob> findByAssetIdAndRequestEventId(UUID assetId, UUID requestEventId);

    ProcessingJob save(ProcessingJob job);

    void delete(ProcessingJob job);
}
