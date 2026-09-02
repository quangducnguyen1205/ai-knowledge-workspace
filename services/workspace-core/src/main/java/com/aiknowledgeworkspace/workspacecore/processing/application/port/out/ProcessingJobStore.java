package com.aiknowledgeworkspace.workspacecore.processing.application.port.out;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobStore {

    Optional<ProcessingJob> findJobById(UUID jobId);

    Optional<ProcessingJob> findByAssetId(UUID assetId);

    Optional<ProcessingJob> findByAssetIdAndRequestEventId(UUID assetId, UUID requestEventId);

    /** Counts the jobs still waiting for a processing result, and when the longest wait began. */
    ProcessingBacklogSnapshot loadBacklogSnapshot();

    ProcessingJob save(ProcessingJob job);

    void delete(ProcessingJob job);
}
