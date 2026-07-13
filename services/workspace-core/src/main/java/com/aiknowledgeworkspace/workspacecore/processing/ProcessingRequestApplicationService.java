package com.aiknowledgeworkspace.workspacecore.processing;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.processing.application.DirectProcessingJobCommand;
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
class ProcessingRequestApplicationService implements ProcessingRequestApplication {

    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingProperties processingProperties;
    private final ProcessingRequestedEventCodec eventCodec;
    private final OutboxWriter outboxWriter;

    ProcessingRequestApplicationService(
            ProcessingJobRepository processingJobRepository,
            ProcessingProperties processingProperties,
            ProcessingRequestedEventCodec eventCodec,
            OutboxWriter outboxWriter
    ) {
        this.processingJobRepository = processingJobRepository;
        this.processingProperties = processingProperties;
        this.eventCodec = eventCodec;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public boolean usesKafkaRequestMode() {
        return processingProperties.getTriggerMode() == ProcessingTriggerMode.KAFKA_REQUEST;
    }

    @Override
    public Optional<ProcessingJobView> findByAssetId(UUID assetId) {
        return processingJobRepository.findByAssetId(assetId).map(this::toView);
    }

    @Override
    public ProcessingJobView createDirectJob(DirectProcessingJobCommand command) {
        return toView(processingJobRepository.save(new ProcessingJob(
                command.assetId(),
                command.fastapiTaskId(),
                command.fastapiVideoId(),
                command.status(),
                command.rawUpstreamTaskState()
        )));
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
                null,
                null,
                ProcessingJobStatus.PENDING,
                "kafka_request_pending"
        );
        job.setProcessingRequestEventId(draft.eventId());
        ProcessingJob savedJob = processingJobRepository.save(job);
        outboxWriter.enqueue(draft);
        return toView(savedJob);
    }

    @Override
    public ProcessingJobView updateJob(ProcessingJobUpdateCommand command) {
        ProcessingJob job = processingJobRepository.findById(command.jobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found: " + command.jobId()));
        job.setProcessingJobStatus(command.status());
        job.setRawUpstreamTaskState(command.rawUpstreamTaskState());
        return toView(processingJobRepository.save(job));
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        processingJobRepository.findByAssetId(assetId).ifPresent(processingJobRepository::delete);
    }

    private ProcessingJobView toView(ProcessingJob job) {
        return new ProcessingJobView(
                job.getId(),
                job.getAssetId(),
                job.getFastapiTaskId(),
                job.getFastapiVideoId(),
                job.getProcessingJobStatus(),
                job.getRawUpstreamTaskState()
        );
    }
}
