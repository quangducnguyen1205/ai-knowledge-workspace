package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingRequestEventFactory;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProcessingRequestApplicationService implements ProcessingRequestUseCase {

    private final ProcessingJobStore processingJobStore;
    private final ProcessingRequestEventFactory eventFactory;
    private final OutboxWriter outboxWriter;

    public ProcessingRequestApplicationService(
            ProcessingJobStore processingJobStore,
            ProcessingRequestEventFactory eventFactory,
            OutboxWriter outboxWriter
    ) {
        this.processingJobStore = processingJobStore;
        this.eventFactory = eventFactory;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public Optional<ProcessingJobView> findByAssetId(UUID assetId) {
        return processingJobStore.findByAssetId(assetId).map(this::toView);
    }

    @Override
    public ProcessingJobView createKafkaJobAndRequest(ProcessingRequestCommand command) {
        return createJobAndRequest(command.assetId(), eventFactory.create(command));
    }

    @Override
    public ProcessingJobView createYouTubeKafkaJobAndRequest(YouTubeProcessingRequestCommand command) {
        return createJobAndRequest(command.assetId(), eventFactory.create(command));
    }

    @Override
    public ProcessingJobView retryKafkaJobAndRequest(ProcessingRequestCommand command) {
        return resetJobAndRequest(command.assetId(), eventFactory.create(command));
    }

    @Override
    public ProcessingJobView retryYouTubeKafkaJobAndRequest(YouTubeProcessingRequestCommand command) {
        return resetJobAndRequest(command.assetId(), eventFactory.create(command));
    }

    private ProcessingJobView createJobAndRequest(UUID assetId, OutboxDraft draft) {
        ProcessingJob job = new ProcessingJob(
                assetId,
                ProcessingJobStatus.PENDING,
                "processing_request_pending"
        );
        job.setProcessingRequestEventId(draft.eventId());
        ProcessingJob savedJob = processingJobStore.save(job);
        outboxWriter.enqueue(draft);
        return toView(savedJob);
    }

    private ProcessingJobView resetJobAndRequest(UUID assetId, OutboxDraft draft) {
        ProcessingJob job = processingJobStore.findByAssetId(assetId)
                .orElseThrow(() -> new IllegalStateException("Processing job was not found for retry"));
        job.setProcessingJobStatus(ProcessingJobStatus.PENDING);
        job.setRawUpstreamTaskState("processing_request_pending");
        job.setProcessingRequestEventId(draft.eventId());
        ProcessingJob savedJob = processingJobStore.save(job);
        outboxWriter.enqueue(draft);
        return toView(savedJob);
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        processingJobStore.findByAssetId(assetId).ifPresent(processingJobStore::delete);
    }

    private ProcessingJobView toView(ProcessingJob job) {
        return new ProcessingJobView(
                job.getId(),
                job.getAssetId(),
                job.getProcessingJobStatus(),
                job.getRawUpstreamTaskState()
        );
    }
}
