package com.aiknowledgeworkspace.workspacecore.processing.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingResultEventStore;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProcessingPersistenceAdapter implements ProcessingJobStore, ProcessingResultEventStore {

    private final ProcessingJobJpaRepository jobRepository;
    private final ProcessingResultEventJpaRepository resultEventRepository;

    ProcessingPersistenceAdapter(
            ProcessingJobJpaRepository jobRepository,
            ProcessingResultEventJpaRepository resultEventRepository
    ) {
        this.jobRepository = jobRepository;
        this.resultEventRepository = resultEventRepository;
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

    @Override
    public Optional<ConsumedProcessingResultEvent> findEventById(UUID eventId) {
        return resultEventRepository.findById(eventId);
    }

    @Override
    public ConsumedProcessingResultEvent save(ConsumedProcessingResultEvent event) {
        return resultEventRepository.save(event);
    }
}
