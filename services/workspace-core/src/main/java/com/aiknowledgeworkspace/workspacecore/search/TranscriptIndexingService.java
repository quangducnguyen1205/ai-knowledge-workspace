package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetReadService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.ProcessingJobNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.TranscriptUnavailableException;
import com.aiknowledgeworkspace.workspacecore.outbox.AssetIndexingRequestedPayload;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TranscriptIndexingService {

    private final ProcessingJobRepository processingJobRepository;
    private final AssetReadService assetReadService;
    private final AssetSearchabilityService assetSearchabilityService;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexRequestService searchIndexRequestService;
    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final AssetSearchIndexingExecutor searchIndexingExecutor;
    private final AssetIndexingEventParser indexingEventParser;

    public TranscriptIndexingService(
            ProcessingJobRepository processingJobRepository,
            AssetReadService assetReadService,
            AssetSearchabilityService assetSearchabilityService,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexRequestService searchIndexRequestService,
            AssetSearchIndexJobRepository searchIndexJobRepository,
            AssetSearchIndexingExecutor searchIndexingExecutor,
            AssetIndexingEventParser indexingEventParser
    ) {
        this.processingJobRepository = processingJobRepository;
        this.assetReadService = assetReadService;
        this.assetSearchabilityService = assetSearchabilityService;
        this.fingerprintService = fingerprintService;
        this.searchIndexRequestService = searchIndexRequestService;
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.searchIndexingExecutor = searchIndexingExecutor;
        this.indexingEventParser = indexingEventParser;
    }

    public AssetIndexResponse indexAssetTranscript(UUID assetId) {
        ProcessingJob processingJob = processingJobRepository.findByAssetId(assetId)
                .orElseThrow(ProcessingJobNotFoundException::new);
        if (processingJob.getProcessingJobStatus() != ProcessingJobStatus.SUCCEEDED) {
            throw new TranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Transcript is not ready until processing reaches terminal success"
            );
        }

        AssetIndexingSource indexingSource = assetReadService.loadAuthorizedIndexingSourceForCompletedProcessing(
                assetId,
                processingJob.getFastapiVideoId()
        );
        String snapshotFingerprint = fingerprintService.fingerprint(indexingSource.transcriptRows());
        AssetSearchIndexJob indexingJob = searchIndexRequestService.createExplicitJob(assetId, snapshotFingerprint);

        try {
            AssetSearchIndexExecutionResult result = searchIndexingExecutor.indexJob(indexingJob.getId());
            if (result.status() != AssetSearchIndexJobStatus.INDEXED) {
                throw new ElasticsearchIntegrationException("Asset transcript indexing did not complete: " + result.status());
            }
        } catch (ElasticsearchIntegrationException exception) {
            assetSearchabilityService.markTranscriptReady(assetId);
            throw exception;
        }

        return new AssetIndexResponse(assetId, AssetStatus.SEARCHABLE, indexingSource.transcriptRows().size());
    }

    public AssetIndexingHandleResult handleIndexingEvent(String rawEventJson) {
        AssetIndexingEventEnvelope event = indexingEventParser.parse(rawEventJson);
        AssetIndexingRequestedPayload payload = event.payload();
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(payload.indexingJobId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found: " + payload.indexingJobId()
                ));

        validateEventMatchesJob(event, indexingJob);

        AssetSearchIndexExecutionResult result = searchIndexingExecutor.indexJob(indexingJob.getId());
        return new AssetIndexingHandleResult(
                event.eventId(),
                indexingJob.getId(),
                result.status(),
                result.indexedDocumentCount()
        );
    }

    private void validateEventMatchesJob(AssetIndexingEventEnvelope event, AssetSearchIndexJob indexingJob) {
        AssetIndexingRequestedPayload payload = event.payload();
        if (!indexingJob.getAssetId().equals(event.aggregateId())) {
            throw new AssetIndexingEventRejectedException("Indexing event aggregateId did not match job assetId");
        }
        if (!indexingJob.getAssetId().equals(payload.assetId())) {
            throw new AssetIndexingEventRejectedException("Indexing event payload assetId did not match job assetId");
        }
        if (!indexingJob.getId().equals(payload.indexingJobId())) {
            throw new AssetIndexingEventRejectedException("Indexing event payload indexingJobId did not match job");
        }
        if (!indexingJob.getSnapshotFingerprint().equals(payload.snapshotFingerprint())) {
            throw new AssetIndexingEventRejectedException("Indexing event snapshot fingerprint did not match job");
        }
        if (indexingJob.getRequestOutboxEventId() != null
                && !indexingJob.getRequestOutboxEventId().equals(event.eventId())) {
            throw new AssetIndexingEventRejectedException("Indexing event ID did not match job request outbox event ID");
        }
    }
}
