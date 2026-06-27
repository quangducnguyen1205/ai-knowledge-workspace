package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetReadService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class AssetSearchIndexingExecutor {

    private static final int MAX_ERROR_DETAIL_LENGTH = 1024;

    private final AssetSearchIndexJobRepository searchIndexJobRepository;
    private final AssetReadService assetReadService;
    private final AssetSearchabilityService assetSearchabilityService;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;
    private final TransactionTemplate transactionTemplate;

    public AssetSearchIndexingExecutor(
            AssetSearchIndexJobRepository searchIndexJobRepository,
            AssetReadService assetReadService,
            AssetSearchabilityService assetSearchabilityService,
            TranscriptSnapshotFingerprintService fingerprintService,
            TranscriptSearchIndexClient transcriptSearchIndexClient,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.assetReadService = assetReadService;
        this.assetSearchabilityService = assetSearchabilityService;
        this.fingerprintService = fingerprintService;
        this.transcriptSearchIndexClient = transcriptSearchIndexClient;
        this.transcriptIndexDocumentMapper = transcriptIndexDocumentMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AssetSearchIndexExecutionResult indexJob(UUID indexingJobId) {
        IndexingAttempt attempt = transactionTemplate.execute(status -> beginIndexingAttempt(indexingJobId));
        if (attempt.result() != null) {
            return attempt.result();
        }

        writeToElasticsearch(attempt.indexingSource());

        return transactionTemplate.execute(status -> finalizeSuccessfulAttempt(attempt.indexingJobId()));
    }

    public void markJobFailed(UUID indexingJobId, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(status -> searchIndexJobRepository.findById(indexingJobId)
                .ifPresent(indexingJob -> {
                    indexingJob.markFailed(safeErrorDetail(exception));
                    searchIndexJobRepository.save(indexingJob);
                }));
    }

    private IndexingAttempt beginIndexingAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(indexingJobId)
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found: " + indexingJobId
                ));

        if (indexingJob.getStatus() == AssetSearchIndexJobStatus.INDEXED
                || indexingJob.getStatus() == AssetSearchIndexJobStatus.SUPERSEDED
                || indexingJob.getStatus() == AssetSearchIndexJobStatus.FAILED) {
            return IndexingAttempt.completed(new AssetSearchIndexExecutionResult(
                    indexingJob.getId(),
                    indexingJob.getStatus(),
                    0
            ));
        }

        AssetIndexingSource indexingSource = assetReadService.findCurrentIndexingSource(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));
        List<AssetTranscriptRowView> transcriptRows = indexingSource.transcriptRows();
        if (transcriptRows.isEmpty()) {
            indexingJob.markFailed("Transcript snapshot was empty or unusable for indexing");
            searchIndexJobRepository.save(indexingJob);
            return IndexingAttempt.completed(new AssetSearchIndexExecutionResult(
                    indexingJob.getId(),
                    indexingJob.getStatus(),
                    0
            ));
        }

        String currentSnapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        if (!indexingJob.getSnapshotFingerprint().equals(currentSnapshotFingerprint)) {
            indexingJob.markSuperseded();
            searchIndexJobRepository.save(indexingJob);
            return IndexingAttempt.completed(new AssetSearchIndexExecutionResult(
                    indexingJob.getId(),
                    indexingJob.getStatus(),
                    0
            ));
        }

        indexingJob.markIndexing();
        searchIndexJobRepository.save(indexingJob);

        return IndexingAttempt.started(indexingJob.getId(), indexingSource);
    }

    private void writeToElasticsearch(AssetIndexingSource indexingSource) {
        transcriptSearchIndexClient.ensureTranscriptIndexExists();
        transcriptSearchIndexClient.deleteTranscriptRowsForAsset(indexingSource.assetId());
        transcriptSearchIndexClient.indexTranscriptRows(toIndexOperations(indexingSource));
        transcriptSearchIndexClient.refreshTranscriptIndex();
    }

    private AssetSearchIndexExecutionResult finalizeSuccessfulAttempt(UUID indexingJobId) {
        AssetSearchIndexJob indexingJob = searchIndexJobRepository.findById(indexingJobId)
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset search index job was not found after indexing: " + indexingJobId
                ));

        if (indexingJob.getStatus() == AssetSearchIndexJobStatus.INDEXED
                || indexingJob.getStatus() == AssetSearchIndexJobStatus.SUPERSEDED
                || indexingJob.getStatus() == AssetSearchIndexJobStatus.FAILED) {
            return new AssetSearchIndexExecutionResult(indexingJob.getId(), indexingJob.getStatus(), 0);
        }
        if (indexingJob.getStatus() != AssetSearchIndexJobStatus.INDEXING) {
            throw new IllegalStateException("Asset search index job was not eligible to finalize: "
                    + indexingJob.getId()
                    + " status="
                    + indexingJob.getStatus());
        }

        AssetIndexingSource indexingSource = assetReadService.findCurrentIndexingSource(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));
        List<AssetTranscriptRowView> transcriptRows = indexingSource.transcriptRows();
        if (transcriptRows.isEmpty()) {
            indexingJob.markSuperseded();
            searchIndexJobRepository.save(indexingJob);
            return new AssetSearchIndexExecutionResult(indexingJob.getId(), indexingJob.getStatus(), 0);
        }
        String currentSnapshotFingerprint = fingerprintService.fingerprint(transcriptRows);
        if (!indexingJob.getSnapshotFingerprint().equals(currentSnapshotFingerprint)) {
            indexingJob.markSuperseded();
            searchIndexJobRepository.save(indexingJob);
            return new AssetSearchIndexExecutionResult(indexingJob.getId(), indexingJob.getStatus(), 0);
        }

        indexingJob.markIndexed(java.time.Instant.now());
        searchIndexJobRepository.save(indexingJob);
        try {
            assetSearchabilityService.markSearchable(indexingSource.assetId());
        } catch (AssetNotFoundException exception) {
            throw new AssetIndexingEventRejectedException(
                    "Asset was not found for search indexing job: " + indexingJob.getAssetId()
            );
        }
        return new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                indexingJob.getStatus(),
                transcriptRows.size()
        );
    }

    private List<TranscriptSearchIndexClient.TranscriptIndexOperation> toIndexOperations(AssetIndexingSource indexingSource) {
        return indexingSource.transcriptRows().stream()
                .map(transcriptRow -> new TranscriptSearchIndexClient.TranscriptIndexOperation(
                        transcriptIndexDocumentMapper.toDocumentId(indexingSource, transcriptRow),
                        transcriptIndexDocumentMapper.toDocument(
                                indexingSource,
                                transcriptRow,
                                AssetStatus.SEARCHABLE
                        )
                ))
                .toList();
    }

    private String safeErrorDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() <= MAX_ERROR_DETAIL_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_DETAIL_LENGTH);
    }

    private record IndexingAttempt(
            UUID indexingJobId,
            AssetIndexingSource indexingSource,
            AssetSearchIndexExecutionResult result
    ) {

        static IndexingAttempt completed(AssetSearchIndexExecutionResult result) {
            return new IndexingAttempt(result.indexingJobId(), null, result);
        }

        static IndexingAttempt started(
                UUID indexingJobId,
                AssetIndexingSource indexingSource
        ) {
            return new IndexingAttempt(indexingJobId, indexingSource, null);
        }
    }
}
