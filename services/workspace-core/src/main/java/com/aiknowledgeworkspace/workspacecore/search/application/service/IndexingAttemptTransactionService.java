package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.IndexingFailureDiagnostic.Category;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.IndexingFailureDiagnostic.FailureStage;

import com.aiknowledgeworkspace.workspacecore.search.application.service.AssetSearchIndexExecutionResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.IndexingFailureDiagnostic;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.service.AssetIndexingEventRejectedException;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class IndexingAttemptTransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexingAttemptTransactionService.class);
    private static final int MAX_ERROR_DETAIL_LENGTH = 1024;

    private final SearchIndexJobStore searchIndexJobStore;
    private final IndexingAssetPort indexingAssetPort;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final TransactionTemplate transactionTemplate;

    public IndexingAttemptTransactionService(
            SearchIndexJobStore searchIndexJobStore,
            IndexingAssetPort indexingAssetPort,
            TranscriptSnapshotFingerprintService fingerprintService,
            PlatformTransactionManager transactionManager
    ) {
        this.searchIndexJobStore = searchIndexJobStore;
        this.indexingAssetPort = indexingAssetPort;
        this.fingerprintService = fingerprintService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public IndexingAttempt beginAttempt(UUID indexingJobId) {
        return transactionTemplate.execute(status -> beginIndexingAttempt(indexingJobId));
    }

    public AssetSearchIndexExecutionResult finalizeSuccessfulAttempt(UUID indexingJobId) {
        return transactionTemplate.execute(status -> finalizeAttempt(indexingJobId));
    }

    public void markJobFailed(UUID indexingJobId, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(status -> searchIndexJobStore.findById(indexingJobId)
                .ifPresent(indexingJob -> {
                    indexingJob.markFailed(safeErrorDetail(exception));
                    searchIndexJobStore.save(indexingJob);
                }));
    }

    public void persistBestEffortDiagnostic(UUID indexingJobId, String diagnostic) {
        try {
            transactionTemplate.executeWithoutResult(status -> searchIndexJobStore.findById(indexingJobId)
                    .ifPresent(indexingJob -> {
                        indexingJob.recordLastError(diagnostic);
                        searchIndexJobStore.save(indexingJob);
                    }));
        } catch (RuntimeException diagnosticPersistenceFailure) {
            // Preserve the original indexing failure; diagnostics are best-effort.
        }
    }

    private IndexingAttempt beginIndexingAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobStore.findById(indexingJobId)
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found: " + indexingJobId
                ));

        if (isTerminal(indexingJob)) {
            return completed(indexingJob);
        }

        IndexingAssetSource indexingSource = indexingAssetPort.findCurrentIndexingSource(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        if (transcriptRows.isEmpty()) {
            indexingJob.markFailed(IndexingFailureDiagnostic.from(
                    List.of(), Category.INDEXING_SOURCE_INVALID, FailureStage.BEFORE_BULK, null
            ));
            searchIndexJobStore.save(indexingJob);
            LOGGER.warn(
                    "Indexing failed assetId={} indexingJobId={} failureCategory={}",
                    indexingJob.getAssetId(),
                    indexingJob.getId(),
                    Category.INDEXING_SOURCE_INVALID
            );
            return completed(indexingJob);
        }

        String currentSnapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        if (!indexingJob.getSnapshotFingerprint().equals(currentSnapshotFingerprint)) {
            indexingJob.markSuperseded();
            searchIndexJobStore.save(indexingJob);
            LOGGER.info(
                    "Indexing superseded assetId={} indexingJobId={}",
                    indexingJob.getAssetId(),
                    indexingJob.getId()
            );
            return completed(indexingJob);
        }

        indexingJob.markIndexing();
        searchIndexJobStore.save(indexingJob);
        LOGGER.info(
                "Indexing started assetId={} indexingJobId={}",
                indexingJob.getAssetId(),
                indexingJob.getId()
        );
        return IndexingAttempt.started(indexingJob.getId(), indexingSource);
    }

    private AssetSearchIndexExecutionResult finalizeAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobStore.findById(indexingJobId)
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found after indexing: " + indexingJobId
                ));
        if (isTerminal(indexingJob)) {
            return result(indexingJob, 0);
        }
        if (indexingJob.getStatus() != AssetSearchIndexJobStatus.INDEXING) {
            throw new IllegalStateException("Asset search index job was not eligible to finalize: "
                    + indexingJob.getId() + " status=" + indexingJob.getStatus());
        }

        IndexingAssetSource indexingSource = indexingAssetPort.findCurrentIndexingSource(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        if (transcriptRows.isEmpty()
                || !indexingJob.getSnapshotFingerprint().equals(fingerprintService.fingerprint(transcriptRows))) {
            indexingJob.markSuperseded();
            searchIndexJobStore.save(indexingJob);
            LOGGER.info(
                    "Indexing superseded assetId={} indexingJobId={}",
                    indexingJob.getAssetId(),
                    indexingJob.getId()
            );
            return result(indexingJob, 0);
        }

        indexingJob.markIndexed(java.time.Instant.now());
        searchIndexJobStore.save(indexingJob);
        try {
            indexingAssetPort.markSearchable(indexingSource.assetId());
        } catch (SearchAssetUnavailableException exception) {
            throw new AssetIndexingEventRejectedException(
                    "Asset was not found for search indexing job: " + indexingJob.getAssetId()
            );
        }
        LOGGER.info(
                "Indexing completed assetId={} indexingJobId={} indexedRowCount={}",
                indexingJob.getAssetId(),
                indexingJob.getId(),
                transcriptRows.size()
        );
        return result(indexingJob, transcriptRows.size());
    }

    private boolean isTerminal(AssetSearchIndexJob job) {
        return job.getStatus() == AssetSearchIndexJobStatus.INDEXED
                || job.getStatus() == AssetSearchIndexJobStatus.SUPERSEDED
                || job.getStatus() == AssetSearchIndexJobStatus.FAILED;
    }

    private IndexingAttempt completed(AssetSearchIndexJob job) {
        return IndexingAttempt.completed(result(job, 0));
    }

    private AssetSearchIndexExecutionResult result(AssetSearchIndexJob job, int count) {
        return new AssetSearchIndexExecutionResult(job.getId(), job.getStatus(), count);
    }

    private String safeErrorDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_ERROR_DETAIL_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_DETAIL_LENGTH);
    }
}
