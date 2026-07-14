package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingResultAssetPort;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.ProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactAccessException;
import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.TranscriptArtifactGateway;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class ApplyProcessingResultApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplyProcessingResultApplicationService.class);
    private static final int MAX_ERROR_DETAIL_LENGTH = 1024;
    private static final int MAX_RAW_UPSTREAM_STATE_LENGTH = 64;

    private final ProcessingResultInbox processingResultInbox;
    private final ProcessingJobRepository processingJobRepository;
    private final TranscriptArtifactGateway transcriptArtifactGateway;
    private final ProcessingResultAssetPort processingResultAssetPort;

    ApplyProcessingResultApplicationService(
            ProcessingResultInbox processingResultInbox,
            ProcessingJobRepository processingJobRepository,
            TranscriptArtifactGateway transcriptArtifactGateway,
            ProcessingResultAssetPort processingResultAssetPort
    ) {
        this.processingResultInbox = processingResultInbox;
        this.processingJobRepository = processingJobRepository;
        this.transcriptArtifactGateway = transcriptArtifactGateway;
        this.processingResultAssetPort = processingResultAssetPort;
    }

    @Transactional
    ProcessingResultHandleResult apply(ApplyProcessingResultCommand command) {
        ProcessingResultEventEnvelope event = command.event();
        ConsumedProcessingResultEvent consumedEvent = processingResultInbox.loadOrCreate(event);
        var duplicate = processingResultInbox.completedOrBlockedDuplicate(
                consumedEvent, command.manualRecovery()
        );
        if (duplicate.isPresent()) {
            LOGGER.info(
                    "Ignoring duplicate processing result event {} with durable status {}",
                    event.eventId(), consumedEvent.getStatus()
            );
            return duplicate.orElseThrow();
        }

        processingResultInbox.markReceivedForApplication(consumedEvent);
        try {
            applyBusinessChange(event);
            processingResultInbox.markApplied(consumedEvent);
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), true);
        } catch (ProcessingResultEventApplyException | TranscriptArtifactAccessException exception) {
            String safeError = safeErrorDetail(exception);
            LOGGER.warn("Processing result event {} could not be applied safely: {}", event.eventId(), safeError);
            processingResultInbox.markFailed(consumedEvent, safeError, command.recoverableEventJson());
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), false);
        }
    }

    private void applyBusinessChange(ProcessingResultEventEnvelope event) {
        switch (event.eventType()) {
            case ProcessingResultEventParser.TRANSCRIPT_READY -> applyTranscriptReady(event);
            case ProcessingResultEventParser.ASSET_PROCESSING_FAILED -> applyProcessingFailed(event);
            default -> throw new ProcessingResultEventApplyException(
                    "Unsupported processing result event type: " + event.eventType()
            );
        }
    }

    private void applyTranscriptReady(ProcessingResultEventEnvelope event) {
        TranscriptReadyPayload payload = (TranscriptReadyPayload) event.payload();
        ProcessingJob processingJob = loadProcessingJob(event.aggregateId(), event.causationEventId());
        List<ProcessingTranscriptRow> transcriptRows = transcriptArtifactGateway.loadValidatedRows(
                payload.processingRequestId()
        );
        applyAssetTranscriptReady(event.aggregateId(), transcriptRows);
        processingJob.setProcessingJobStatus(ProcessingJobStatus.SUCCEEDED);
        processingJob.setRawUpstreamTaskState("transcript.ready");
        processingJobRepository.save(processingJob);
    }

    private void applyProcessingFailed(ProcessingResultEventEnvelope event) {
        AssetProcessingFailedPayload payload = (AssetProcessingFailedPayload) event.payload();
        ProcessingJob processingJob = loadProcessingJob(event.aggregateId(), event.causationEventId());
        processingJob.setProcessingJobStatus(ProcessingJobStatus.FAILED);
        processingJob.setRawUpstreamTaskState(safeRawUpstreamState(payload));
        processingJobRepository.save(processingJob);
        applyAssetProcessingFailed(event.aggregateId());
    }

    private ProcessingJob loadProcessingJob(UUID assetId, UUID processingRequestEventId) {
        return processingJobRepository.findByAssetIdAndProcessingRequestEventId(assetId, processingRequestEventId)
                .orElseThrow(() -> new ProcessingResultEventApplyException(
                        "Processing job was not found for result event request correlation"
                ));
    }

    private void applyAssetTranscriptReady(UUID assetId, List<ProcessingTranscriptRow> transcriptRows) {
        try {
            processingResultAssetPort.applyTranscriptReady(assetId, transcriptRows);
        } catch (ProcessingAssetUnavailableException exception) {
            throw new ProcessingResultEventApplyException("Asset was not found for result event", exception);
        }
    }

    private void applyAssetProcessingFailed(UUID assetId) {
        try {
            processingResultAssetPort.applyProcessingFailed(assetId);
        } catch (ProcessingAssetUnavailableException exception) {
            throw new ProcessingResultEventApplyException("Asset was not found for result event", exception);
        }
    }

    private String safeRawUpstreamState(AssetProcessingFailedPayload payload) {
        String errorCode = StringUtils.hasText(payload.errorCode()) ? payload.errorCode() : "processing_failed";
        return abbreviate(errorCode.trim().toLowerCase(Locale.ROOT), MAX_RAW_UPSTREAM_STATE_LENGTH);
    }

    private String safeErrorDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        return abbreviate(message.replaceAll("\\s+", " ").trim(), MAX_ERROR_DETAIL_LENGTH);
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
