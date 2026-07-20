package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProcessingJobPersistenceAdapter implements ProcessingJobStore {

    private final ProcessingJobJpaRepository jobRepository;

    ProcessingJobPersistenceAdapter(ProcessingJobJpaRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public Optional<ProcessingJob> findJobById(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    public Optional<ProcessingJob> findByAssetId(UUID assetId) {
        return jobRepository.findByAssetId(assetId);
    }

    @Override
    public Optional<ProcessingJob> findByAssetIdAndRequestEventId(UUID assetId, UUID requestEventId) {
        return jobRepository.findByAssetIdAndProcessingRequestEventId(assetId, requestEventId);
    }

    @Override
    public ProcessingJob save(ProcessingJob job) {
        return jobRepository.save(job);
    }

    @Override
    public void delete(ProcessingJob job) {
        jobRepository.delete(job);
    }

}
