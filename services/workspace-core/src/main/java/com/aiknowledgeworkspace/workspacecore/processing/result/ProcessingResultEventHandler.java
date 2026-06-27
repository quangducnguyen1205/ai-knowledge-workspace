package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetProcessingResultApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProcessingResultEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingResultEventHandler.class);
    private static final int MAX_ERROR_DETAIL_LENGTH = 1024;
    private static final int MAX_RAW_UPSTREAM_STATE_LENGTH = 64;
    private static final int MAX_RECOVERABLE_EVENT_JSON_LENGTH = 8192;

    private final ProcessingResultEventParser eventParser;
    private final ConsumedProcessingResultEventRepository consumedEventRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FastApiProcessingClient fastApiProcessingClient;
    private final TranscriptArtifactValidator transcriptArtifactValidator;
    private final AssetProcessingResultApplicationService assetProcessingResultApplicationService;

    public ProcessingResultEventHandler(
            ProcessingResultEventParser eventParser,
            ConsumedProcessingResultEventRepository consumedEventRepository,
            ProcessingJobRepository processingJobRepository,
            FastApiProcessingClient fastApiProcessingClient,
            TranscriptArtifactValidator transcriptArtifactValidator,
            AssetProcessingResultApplicationService assetProcessingResultApplicationService
    ) {
        this.eventParser = eventParser;
        this.consumedEventRepository = consumedEventRepository;
        this.processingJobRepository = processingJobRepository;
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.transcriptArtifactValidator = transcriptArtifactValidator;
        this.assetProcessingResultApplicationService = assetProcessingResultApplicationService;
    }

    @Transactional
    public ProcessingResultHandleResult handle(String rawEventJson) {
        return handle(rawEventJson, false);
    }

    @Transactional
    public ProcessingResultHandleResult recoverFailedEvent(UUID eventId) {
        ConsumedProcessingResultEvent consumedEvent = consumedEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Consumed processing result event was not found: " + eventId));
        if (consumedEvent.getStatus() != ConsumedProcessingResultEventStatus.FAILED) {
            throw new IllegalStateException(
                    "Consumed processing result event is not FAILED: " + eventId + " status=" + consumedEvent.getStatus()
            );
        }
        if (!StringUtils.hasText(consumedEvent.getRecoverableEventJson())) {
            throw new IllegalStateException(
                    "Consumed processing result event does not have a recoverable event envelope: " + eventId
            );
        }
        ProcessingResultEventEnvelope recoverableEvent = parseEvent(consumedEvent.getRecoverableEventJson());
        if (!eventId.equals(recoverableEvent.eventId())) {
            throw new IllegalStateException("Recoverable processing result event ID did not match requested event ID");
        }

        ProcessingResultHandleResult result = handle(consumedEvent.getRecoverableEventJson(), true);
        return result;
    }

    private ProcessingResultHandleResult handle(String rawEventJson, boolean manualRecovery) {
        ProcessingResultEventEnvelope event = parseEvent(rawEventJson);
        String recoverableEventJson = recoverableEventJson(rawEventJson, event);
        ConsumedProcessingResultEvent consumedEvent = consumedEventRepository.findById(event.eventId())
                .orElseGet(() -> new ConsumedProcessingResultEvent(
                        event.eventId(),
                        event.eventType(),
                        event.aggregateId(),
                        event.causationEventId(),
                        event.occurredAt()
                ));

        if (consumedEvent.getStatus() == ConsumedProcessingResultEventStatus.APPLIED) {
            LOGGER.info("Ignoring duplicate already-applied processing result event {}", event.eventId());
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), false);
        }

        if (consumedEvent.getStatus() == ConsumedProcessingResultEventStatus.FAILED && !manualRecovery) {
            LOGGER.info("Ignoring duplicate durable failed processing result event {}", event.eventId());
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), false);
        }

        consumedEvent.markReceivedForRetry();
        consumedEventRepository.save(consumedEvent);

        try {
            applyBusinessChange(event);
            consumedEvent.markApplied();
            consumedEventRepository.save(consumedEvent);
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), true);
        } catch (ProcessingResultEventApplyException | FastApiIntegrationException exception) {
            String safeError = safeErrorDetail(exception);
            LOGGER.warn(
                    "Processing result event {} could not be applied safely: {}",
                    event.eventId(),
                    safeError
            );
            consumedEvent.markFailed(safeError, recoverableEventJson);
            consumedEventRepository.save(consumedEvent);
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), false);
        }
    }

    private ProcessingResultEventEnvelope parseEvent(String rawEventJson) {
        try {
            return eventParser.parse(rawEventJson);
        } catch (ProcessingResultEventRejectedException exception) {
            LOGGER.warn("Rejected processing result event: {}", exception.getMessage());
            throw exception;
        }
    }

    private String recoverableEventJson(String rawEventJson, ProcessingResultEventEnvelope event) {
        String recoverableEventJson = eventParser.recoverableEnvelopeJson(rawEventJson, event);
        if (recoverableEventJson.length() > MAX_RECOVERABLE_EVENT_JSON_LENGTH) {
            throw new ProcessingResultEventRejectedException("Processing result event recoverable envelope exceeded safe length");
        }
        return recoverableEventJson;
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

        List<FastApiTranscriptRowResponse> artifactRows = fastApiProcessingClient.getTranscriptArtifactRows(
                payload.processingRequestId().toString()
        );
        List<FastApiTranscriptRowResponse> validatedRows = transcriptArtifactValidator.validate(artifactRows);

        applyAssetTranscriptReady(event.aggregateId(), validatedRows);
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

    private void applyAssetTranscriptReady(UUID assetId, List<FastApiTranscriptRowResponse> transcriptRows) {
        try {
            assetProcessingResultApplicationService.applyTranscriptReady(
                    assetId,
                    transcriptRows.stream()
                            .map(this::toAssetTranscriptRowInput)
                            .toList()
            );
        } catch (AssetNotFoundException exception) {
            throw new ProcessingResultEventApplyException("Asset was not found for result event", exception);
        }
    }

    private void applyAssetProcessingFailed(UUID assetId) {
        try {
            assetProcessingResultApplicationService.applyProcessingFailed(assetId);
        } catch (AssetNotFoundException exception) {
            throw new ProcessingResultEventApplyException("Asset was not found for result event", exception);
        }
    }

    private AssetTranscriptRowInput toAssetTranscriptRowInput(FastApiTranscriptRowResponse transcriptRow) {
        return new AssetTranscriptRowInput(
                transcriptRow.id(),
                transcriptRow.videoId(),
                transcriptRow.segmentIndex(),
                transcriptRow.text(),
                transcriptRow.createdAt()
        );
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
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
