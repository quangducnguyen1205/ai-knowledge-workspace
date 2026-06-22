package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshotRepository;
import java.time.Instant;
import java.util.Comparator;
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
    private final AssetRepository assetRepository;
    private final AssetTranscriptRowSnapshotRepository transcriptRowSnapshotRepository;
    private final TranscriptSnapshotFingerprintService fingerprintService;
    private final TranscriptSearchIndexClient transcriptSearchIndexClient;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;
    private final TransactionTemplate transactionTemplate;

    public AssetSearchIndexingExecutor(
            AssetSearchIndexJobRepository searchIndexJobRepository,
            AssetRepository assetRepository,
            AssetTranscriptRowSnapshotRepository transcriptRowSnapshotRepository,
            TranscriptSnapshotFingerprintService fingerprintService,
            TranscriptSearchIndexClient transcriptSearchIndexClient,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.searchIndexJobRepository = searchIndexJobRepository;
        this.assetRepository = assetRepository;
        this.transcriptRowSnapshotRepository = transcriptRowSnapshotRepository;
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

        writeToElasticsearch(attempt.asset(), attempt.transcriptRows());

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

        Asset asset = assetRepository.findById(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));
        List<AssetTranscriptRowSnapshot> transcriptRows = loadUsableTranscriptSnapshot(asset.getId());
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

        return IndexingAttempt.started(indexingJob.getId(), asset, transcriptRows);
    }

    private void writeToElasticsearch(Asset asset, List<AssetTranscriptRowSnapshot> transcriptRows) {
        transcriptSearchIndexClient.ensureTranscriptIndexExists();
        transcriptSearchIndexClient.deleteTranscriptRowsForAsset(asset.getId());
        transcriptSearchIndexClient.indexTranscriptRows(toIndexOperations(asset, transcriptRows));
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

        List<AssetTranscriptRowSnapshot> transcriptRows = loadUsableTranscriptSnapshot(indexingJob.getAssetId());
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

        Asset asset = assetRepository.findById(indexingJob.getAssetId())
                .orElseThrow(() -> new AssetIndexingEventRejectedException(
                        "Asset was not found for search indexing job: " + indexingJob.getAssetId()
                ));

        indexingJob.markIndexed(Instant.now());
        asset.setStatus(AssetStatus.SEARCHABLE);
        searchIndexJobRepository.save(indexingJob);
        assetRepository.save(asset);
        return new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                indexingJob.getStatus(),
                transcriptRows.size()
        );
    }

    private List<AssetTranscriptRowSnapshot> loadUsableTranscriptSnapshot(UUID assetId) {
        return transcriptRowSnapshotRepository.findByAssetId(assetId).stream()
                .filter(this::isUsableTranscriptRow)
                .sorted(Comparator.comparing(
                        AssetTranscriptRowSnapshot::getSegmentIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<TranscriptSearchIndexClient.TranscriptIndexOperation> toIndexOperations(
            Asset asset,
            List<AssetTranscriptRowSnapshot> transcriptRows
    ) {
        return transcriptRows.stream()
                .map(transcriptRow -> new TranscriptSearchIndexClient.TranscriptIndexOperation(
                        transcriptIndexDocumentMapper.toDocumentId(asset, transcriptRow),
                        transcriptIndexDocumentMapper.toDocument(
                                asset,
                                transcriptRow,
                                AssetStatus.SEARCHABLE
                        )
                ))
                .toList();
    }

    private boolean isUsableTranscriptRow(AssetTranscriptRowSnapshot transcriptRow) {
        return transcriptRow.getSegmentIndex() != null && StringUtils.hasText(transcriptRow.getText());
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
            Asset asset,
            List<AssetTranscriptRowSnapshot> transcriptRows,
            AssetSearchIndexExecutionResult result
    ) {

        static IndexingAttempt completed(AssetSearchIndexExecutionResult result) {
            return new IndexingAttempt(result.indexingJobId(), null, List.of(), result);
        }

        static IndexingAttempt started(
                UUID indexingJobId,
                Asset asset,
                List<AssetTranscriptRowSnapshot> transcriptRows
        ) {
            return new IndexingAttempt(indexingJobId, asset, transcriptRows, null);
        }
    }
}
