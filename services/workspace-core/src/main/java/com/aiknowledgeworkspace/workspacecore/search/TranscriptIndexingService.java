package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingResult;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedPayload;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TranscriptIndexingService implements ExplicitIndexingApplication {

    private final ProcessingRequestApplication processingRequestApplication;
    private final IndexingAssetPort indexingAssetPort;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexRequestService searchIndexRequestService;
    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final AssetSearchIndexingExecutor searchIndexingExecutor;
    private final AssetIndexingEventParser indexingEventParser;

    public TranscriptIndexingService(
            ProcessingRequestApplication processingRequestApplication,
            IndexingAssetPort indexingAssetPort,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexRequestService searchIndexRequestService,
            AssetSearchIndexJobRepository searchIndexJobRepository,
            AssetSearchIndexingExecutor searchIndexingExecutor,
            AssetIndexingEventParser indexingEventParser
    ) {
        this.processingRequestApplication = processingRequestApplication;
        this.indexingAssetPort = indexingAssetPort;
        this.fingerprintService = fingerprintService;
        this.searchIndexRequestService = searchIndexRequestService;
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.searchIndexingExecutor = searchIndexingExecutor;
        this.indexingEventParser = indexingEventParser;
    }

    public ExplicitIndexingResult indexAssetTranscript(UUID assetId) {
        ProcessingJobView processingJob = processingRequestApplication.findByAssetId(assetId)
                .orElseThrow(SearchProcessingJobNotFoundException::new);
        if (processingJob.status() != ProcessingJobStatus.SUCCEEDED) {
            throw new SearchTranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Transcript is not ready until processing reaches terminal success"
            );
        }

        IndexingAssetSource indexingSource;
        try {
            indexingSource = indexingAssetPort.loadAuthorizedIndexingSourceForCompletedProcessing(
                    assetId,
                    processingJob.fastapiVideoId()
            );
        } catch (SearchAssetUnavailableException exception) {
            throw new SearchAssetNotFoundException();
        }
        String snapshotFingerprint = fingerprintService.fingerprint(indexingSource.transcriptRows());
        AssetSearchIndexJob indexingJob = searchIndexRequestService.createExplicitJob(assetId, snapshotFingerprint);

        try {
            AssetSearchIndexExecutionResult result = searchIndexingExecutor.indexJob(indexingJob.getId());
            if (result.status() != AssetSearchIndexJobStatus.INDEXED) {
                throw new ElasticsearchIntegrationException("Asset transcript indexing did not complete: " + result.status());
            }
        } catch (ElasticsearchIntegrationException exception) {
            try {
                indexingAssetPort.markTranscriptReady(assetId);
            } catch (SearchAssetUnavailableException assetUnavailable) {
                throw new SearchAssetNotFoundException();
            }
            throw exception;
        }

        return new ExplicitIndexingResult(assetId, indexingSource.transcriptRows().size());
    }

    public AssetIndexingHandleResult handleIndexingEvent(String rawEventJson) {
        AssetIndexingEventEnvelope event = indexingEventParser.parse(rawEventJson);
        IndexingRequestedPayload payload = event.payload();
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
        IndexingRequestedPayload payload = event.payload();
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
