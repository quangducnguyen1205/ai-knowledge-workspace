package com.aiknowledgeworkspace.workspacecore.search.indexing.integration;

import com.aiknowledgeworkspace.workspacecore.search.indexing.application.AssetSearchIndexExecutionResult;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.ExecuteIndexJobApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;

import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedPayload;
import org.springframework.stereotype.Service;

@Service
public class AssetIndexingEventHandler {

    private final AssetIndexingEventParser indexingEventParser;
    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    public AssetIndexingEventHandler(
            AssetIndexingEventParser indexingEventParser,
            AssetSearchIndexJobRepository searchIndexJobRepository,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService
    ) {
        this.indexingEventParser = indexingEventParser;
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.executeIndexJobApplicationService = executeIndexJobApplicationService;
    }

    public AssetIndexingHandleResult handle(String rawEventJson) {
        AssetIndexingEventEnvelope event = indexingEventParser.parse(rawEventJson);
        IndexingRequestedPayload payload = event.payload();
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(payload.indexingJobId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found: " + payload.indexingJobId()
                ));
        validateEventMatchesJob(event, indexingJob);
        AssetSearchIndexExecutionResult result = executeIndexJobApplicationService.execute(indexingJob.getId());
        return new AssetIndexingHandleResult(
                event.eventId(), indexingJob.getId(), result.status(), result.indexedDocumentCount()
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
