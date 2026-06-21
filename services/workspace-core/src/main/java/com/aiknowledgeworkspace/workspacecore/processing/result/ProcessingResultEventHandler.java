package com.aiknowledgeworkspace.workspacecore.processing.result;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
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

    private final ProcessingResultEventParser eventParser;
    private final ConsumedProcessingResultEventRepository consumedEventRepository;
    private final AssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FastApiProcessingClient fastApiProcessingClient;
    private final TranscriptArtifactValidator transcriptArtifactValidator;
    private final AssetPersistenceService assetPersistenceService;

    public ProcessingResultEventHandler(
            ProcessingResultEventParser eventParser,
            ConsumedProcessingResultEventRepository consumedEventRepository,
            AssetRepository assetRepository,
            ProcessingJobRepository processingJobRepository,
            FastApiProcessingClient fastApiProcessingClient,
            TranscriptArtifactValidator transcriptArtifactValidator,
            AssetPersistenceService assetPersistenceService
    ) {
        this.eventParser = eventParser;
        this.consumedEventRepository = consumedEventRepository;
        this.assetRepository = assetRepository;
        this.processingJobRepository = processingJobRepository;
        this.fastApiProcessingClient = fastApiProcessingClient;
        this.transcriptArtifactValidator = transcriptArtifactValidator;
        this.assetPersistenceService = assetPersistenceService;
    }

    @Transactional
    public ProcessingResultHandleResult handle(String rawEventJson) {
        ProcessingResultEventEnvelope event = parseEvent(rawEventJson);
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

        consumedEvent.markReceivedForRetry();
        consumedEventRepository.save(consumedEvent);

        try {
            applyBusinessChange(event);
            consumedEvent.markApplied();
            consumedEventRepository.save(consumedEvent);
            return new ProcessingResultHandleResult(event.eventId(), consumedEvent.getStatus(), true);
        } catch (RuntimeException exception) {
            String safeError = safeErrorDetail(exception);
            LOGGER.warn(
                    "Processing result event {} could not be applied safely: {}",
                    event.eventId(),
                    safeError
            );
            consumedEvent.markFailed(safeError);
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
        Asset asset = loadAsset(event.aggregateId());
        ProcessingJob processingJob = loadProcessingJob(asset.getId(), event.causationEventId());

        List<FastApiTranscriptRowResponse> artifactRows = fastApiProcessingClient.getTranscriptArtifactRows(
                payload.processingRequestId().toString()
        );
        List<FastApiTranscriptRowResponse> validatedRows = transcriptArtifactValidator.validate(artifactRows);

        assetPersistenceService.replaceTranscriptSnapshot(asset, validatedRows);
        processingJob.setProcessingJobStatus(ProcessingJobStatus.SUCCEEDED);
        processingJob.setRawUpstreamTaskState("transcript.ready");
        processingJobRepository.save(processingJob);

        if (asset.getStatus() != AssetStatus.SEARCHABLE) {
            asset.setStatus(AssetStatus.TRANSCRIPT_READY);
            assetRepository.save(asset);
        }
    }

    private void applyProcessingFailed(ProcessingResultEventEnvelope event) {
        AssetProcessingFailedPayload payload = (AssetProcessingFailedPayload) event.payload();
        Asset asset = loadAsset(event.aggregateId());
        ProcessingJob processingJob = loadProcessingJob(asset.getId(), event.causationEventId());

        processingJob.setProcessingJobStatus(ProcessingJobStatus.FAILED);
        processingJob.setRawUpstreamTaskState(safeRawUpstreamState(payload));
        processingJobRepository.save(processingJob);

        if (asset.getStatus() != AssetStatus.FAILED) {
            asset.setStatus(AssetStatus.FAILED);
            assetRepository.save(asset);
        }
    }

    private Asset loadAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new ProcessingResultEventApplyException("Asset was not found for result event"));
    }

    private ProcessingJob loadProcessingJob(UUID assetId, UUID processingRequestEventId) {
        return processingJobRepository.findByAssetIdAndProcessingRequestEventId(assetId, processingRequestEventId)
                .orElseThrow(() -> new ProcessingResultEventApplyException(
                        "Processing job was not found for result event request correlation"
                ));
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
