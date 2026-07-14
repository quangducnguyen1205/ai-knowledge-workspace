package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.SearchAssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.search.SearchProcessingJobNotFoundException;
import com.aiknowledgeworkspace.workspacecore.search.SearchTranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch.ElasticsearchIntegrationException;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingResult;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetUnavailableException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TranscriptIndexingService implements ExplicitIndexingApplication {

    private final ProcessingRequestApplication processingRequestApplication;
    private final IndexingAssetPort indexingAssetPort;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexRequestService searchIndexRequestService;
    private final ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    public TranscriptIndexingService(
            ProcessingRequestApplication processingRequestApplication,
            IndexingAssetPort indexingAssetPort,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexRequestService searchIndexRequestService,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService
    ) {
        this.processingRequestApplication = processingRequestApplication;
        this.indexingAssetPort = indexingAssetPort;
        this.fingerprintService = fingerprintService;
        this.searchIndexRequestService = searchIndexRequestService;
        this.executeIndexJobApplicationService = executeIndexJobApplicationService;
    }

    @Override
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
                    assetId, processingJob.fastapiVideoId()
            );
        } catch (SearchAssetUnavailableException exception) {
            throw new SearchAssetNotFoundException();
        }
        String snapshotFingerprint = fingerprintService.fingerprint(indexingSource.transcriptRows());
        AssetSearchIndexJob indexingJob = searchIndexRequestService.createExplicitJob(assetId, snapshotFingerprint);

        try {
            AssetSearchIndexExecutionResult result = executeIndexJobApplicationService.execute(indexingJob.getId());
            if (result.status() != AssetSearchIndexJobStatus.INDEXED) {
                throw new ElasticsearchIntegrationException(
                        "Asset transcript indexing did not complete: " + result.status()
                );
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
}
