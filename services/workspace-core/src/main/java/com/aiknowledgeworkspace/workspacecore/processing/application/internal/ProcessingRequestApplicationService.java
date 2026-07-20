package com.aiknowledgeworkspace.workspacecore.processing.application.internal;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.processing.application.KafkaProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobUpdateCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventCodec;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventData;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProcessingRequestApplicationService implements ProcessingRequestApplication {

    private final ProcessingJobStore processingJobStore;
    private final ProcessingRequestedEventCodec eventCodec;
    private final OutboxWriter outboxWriter;

    public ProcessingRequestApplicationService(
            ProcessingJobStore processingJobStore,
            ProcessingRequestedEventCodec eventCodec,
            OutboxWriter outboxWriter
    ) {
        this.processingJobStore = processingJobStore;
        this.eventCodec = eventCodec;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public Optional<ProcessingJobView> findByAssetId(UUID assetId) {
        return processingJobStore.findByAssetId(assetId).map(this::toView);
    }

    @Override
    public ProcessingJobView createKafkaJobAndRequest(KafkaProcessingRequestCommand command) {
        OutboxDraft draft = eventCodec.encode(new ProcessingRequestedEventData(
                command.assetId(),
                command.workspaceId(),
                command.ownerId(),
                command.storageBucket(),
                command.objectKey(),
                command.originalFilename(),
                command.contentType(),
                command.sizeBytes()
        ));
        ProcessingJob job = new ProcessingJob(
                command.assetId(),
                ProcessingJobStatus.PENDING,
                "processing_request_pending"
        );
        job.setProcessingRequestEventId(draft.eventId());
        ProcessingJob savedJob = processingJobStore.save(job);
        outboxWriter.enqueue(draft);
        return toView(savedJob);
    }

    @Override
    public ProcessingJobView updateJob(ProcessingJobUpdateCommand command) {
        ProcessingJob job = processingJobStore.findJobById(command.jobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found: " + command.jobId()));
        job.setProcessingJobStatus(command.status());
        job.setRawUpstreamTaskState(command.rawUpstreamTaskState());
        return toView(processingJobStore.save(job));
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
