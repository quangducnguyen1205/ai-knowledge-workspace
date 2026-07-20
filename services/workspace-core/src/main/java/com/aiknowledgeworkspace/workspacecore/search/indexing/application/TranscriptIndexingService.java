package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.SearchAssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.search.SearchTranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;

import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingResult;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetUnavailableException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TranscriptIndexingService implements ExplicitIndexingApplication {

    private final IndexingAssetPort indexingAssetPort;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final AssetSearchIndexRequestService searchIndexRequestService;
    private final ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    public TranscriptIndexingService(
            IndexingAssetPort indexingAssetPort,
            TranscriptSnapshotFingerprintService fingerprintService,
            AssetSearchIndexRequestService searchIndexRequestService,
            ExecuteIndexJobApplicationService executeIndexJobApplicationService
    ) {
        this.indexingAssetPort = indexingAssetPort;
        this.fingerprintService = fingerprintService;
        this.searchIndexRequestService = searchIndexRequestService;
        this.executeIndexJobApplicationService = executeIndexJobApplicationService;
    }

    @Override
    public ExplicitIndexingResult indexAssetTranscript(UUID assetId) {
        IndexingAssetSource indexingSource;
        try {
            indexingSource = indexingAssetPort.loadAuthorizedIndexingSource(assetId);
        } catch (SearchAssetUnavailableException exception) {
            throw new SearchAssetNotFoundException();
        }
        if (indexingSource.transcriptRows().isEmpty()) {
            throw new SearchTranscriptUnavailableException(
                    "TRANSCRIPT_NOT_READY",
                    "Canonical transcript is unavailable until processing reaches terminal success"
            );
        }
        String snapshotFingerprint = fingerprintService.fingerprint(indexingSource.transcriptRows());
        AssetSearchIndexJob indexingJob = searchIndexRequestService.createExplicitJob(assetId, snapshotFingerprint);

        try {
            AssetSearchIndexExecutionResult result = executeIndexJobApplicationService.execute(indexingJob.getId());
            if (result.status() != AssetSearchIndexJobStatus.INDEXED) {
                throw new SearchIndexOperationException(
                        "Asset transcript indexing did not complete: " + result.status()
                );
            }
        } catch (SearchIndexOperationException exception) {
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
