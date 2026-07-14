package com.aiknowledgeworkspace.workspacecore.search.indexing.transaction;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic.Category;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic.FailureStage;

import com.aiknowledgeworkspace.workspacecore.search.indexing.application.AssetSearchIndexExecutionResult;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingAttempt;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;
import com.aiknowledgeworkspace.workspacecore.search.indexing.integration.AssetIndexingEventRejectedException;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.SearchAssetUnavailableException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class IndexingAttemptTransactionService {

    private static final int MAX_ERROR_DETAIL_LENGTH = 1024;

    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final IndexingAssetPort indexingAssetPort;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final TransactionTemplate transactionTemplate;

    public IndexingAttemptTransactionService(
            AssetSearchIndexJobRepository searchIndexJobRepository,
            IndexingAssetPort indexingAssetPort,
            TranscriptSnapshotFingerprintService fingerprintService,
            PlatformTransactionManager transactionManager
    ) {
        this.searchIndexJobRepository = searchIndexJobRepository;
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
        transactionTemplate.executeWithoutResult(status -> searchIndexJobRepository.findById(indexingJobId)
                .ifPresent(indexingJob -> {
                    indexingJob.markFailed(safeErrorDetail(exception));
                    searchIndexJobRepository.save(indexingJob);
                }));
    }

    public void persistBestEffortDiagnostic(UUID indexingJobId, String diagnostic) {
        try {
            transactionTemplate.executeWithoutResult(status -> searchIndexJobRepository.findById(indexingJobId)
                    .ifPresent(indexingJob -> {
                        indexingJob.recordLastError(diagnostic);
                        searchIndexJobRepository.save(indexingJob);
                    }));
        } catch (RuntimeException diagnosticPersistenceFailure) {
            // Preserve the original indexing failure; diagnostics are best-effort.
        }
    }

    private IndexingAttempt beginIndexingAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(indexingJobId)
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
            searchIndexJobRepository.save(indexingJob);
            return completed(indexingJob);
        }

        String currentSnapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        if (!indexingJob.getSnapshotFingerprint().equals(currentSnapshotFingerprint)) {
            indexingJob.markSuperseded();
            searchIndexJobRepository.save(indexingJob);
            return completed(indexingJob);
        }

        indexingJob.markIndexing();
        searchIndexJobRepository.save(indexingJob);
        return IndexingAttempt.started(indexingJob.getId(), indexingSource);
    }

    private AssetSearchIndexExecutionResult finalizeAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(indexingJobId)
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
            searchIndexJobRepository.save(indexingJob);
            return result(indexingJob, 0);
        }

        indexingJob.markIndexed(java.time.Instant.now());
        searchIndexJobRepository.save(indexingJob);
        try {
            indexingAssetPort.markSearchable(indexingSource.assetId());
        } catch (SearchAssetUnavailableException exception) {
            throw new AssetIndexingEventRejectedException(
                    "Asset was not found for search indexing job: " + indexingJob.getAssetId()
            );
        }
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
