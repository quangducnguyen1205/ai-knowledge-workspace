package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.AssetReadService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.search.IndexingFailureDiagnostic.Category;
import com.aiknowledgeworkspace.workspacecore.search.IndexingFailureDiagnostic.FailureStage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
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

        try {
            writeToElasticsearch(attempt.indexingSource());
        } catch (IndexingWriteFailure failure) {
            recordFailureDiagnostic(attempt, failure);
            throw failure.originalException();
        }

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
            indexingJob.markFailed(IndexingFailureDiagnostic.from(
                    List.of(),
                    Category.INDEXING_SOURCE_INVALID,
                    FailureStage.BEFORE_BULK,
                    null
            ));
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
        runIndexingStep(
                FailureStage.BEFORE_BULK,
                Category.ELASTICSEARCH_RESPONSE_INVALID,
                transcriptSearchIndexClient::ensureTranscriptIndexExists
        );
        runIndexingStep(
                FailureStage.BEFORE_BULK,
                Category.ELASTICSEARCH_RESPONSE_INVALID,
                () -> transcriptSearchIndexClient.deleteTranscriptRowsForAsset(indexingSource.assetId())
        );
        IndexingPlan indexingPlan = runIndexingStep(
                FailureStage.BULK_RESPONSE,
                Category.ELASTICSEARCH_BULK_REJECTED,
                () -> toIndexOperations(indexingSource)
        );
        try {
            runIndexingStep(
                    FailureStage.BULK_RESPONSE,
                    Category.ELASTICSEARCH_BULK_REJECTED,
                    () -> transcriptSearchIndexClient.indexTranscriptRows(indexingPlan.operations())
            );
            runIndexingStep(
                    FailureStage.AFTER_BULK,
                    Category.ELASTICSEARCH_RESPONSE_INVALID,
                    transcriptSearchIndexClient::refreshTranscriptIndex
            );
        } catch (IndexingWriteFailure failure) {
            throw failure.withRowMetadata(indexingPlan.rowMetadata());
        }
    }

    private void runIndexingStep(
            FailureStage integrationFailureStage,
            Category integrationFailureCategory,
            Runnable indexingStep
    ) {
        runIndexingStep(integrationFailureStage, integrationFailureCategory, () -> {
            indexingStep.run();
            return null;
        });
    }

    private <T> T runIndexingStep(
            FailureStage integrationFailureStage,
            Category integrationFailureCategory,
            Supplier<T> indexingStep
    ) {
        try {
            return indexingStep.get();
        } catch (RuntimeException exception) {
            throw new IndexingWriteFailure(
                    failureStage(exception, integrationFailureStage),
                    failureCategory(exception, integrationFailureCategory),
                    exception,
                    null
            );
        }
    }

    private void recordFailureDiagnostic(
            IndexingAttempt attempt,
            IndexingWriteFailure failure
    ) {
        try {
            List<IndexingFailureDiagnostic.RowMetadata> rowMetadata = failure.rowMetadata();
            if (rowMetadata == null) {
                rowMetadata = toIndexOperations(attempt.indexingSource()).rowMetadata();
            }
            String diagnostic = IndexingFailureDiagnostic.from(
                    rowMetadata,
                    failure.category(),
                    failure.failureStage(),
                    failure.originalException()
            );
            transactionTemplate.executeWithoutResult(status -> searchIndexJobRepository.findById(attempt.indexingJobId())
                    .ifPresent(indexingJob -> {
                        indexingJob.recordLastError(diagnostic);
                        searchIndexJobRepository.save(indexingJob);
                    }));
        } catch (RuntimeException diagnosticPersistenceFailure) {
            // Preserve the original indexing failure; diagnostics are best-effort.
        }
    }

    private FailureStage failureStage(RuntimeException exception, FailureStage integrationFailureStage) {
        if (exception instanceof ElasticsearchConnectivityException) {
            return FailureStage.TRANSPORT;
        }
        if (exception instanceof ElasticsearchIntegrationException) {
            return integrationFailureStage;
        }
        return FailureStage.UNEXPECTED;
    }

    private Category failureCategory(RuntimeException exception, Category integrationFailureCategory) {
        if (exception instanceof ElasticsearchConnectivityException) {
            return Category.ELASTICSEARCH_TRANSPORT_FAILURE;
        }
        if (exception instanceof ElasticsearchIntegrationException) {
            return integrationFailureCategory;
        }
        return Category.INDEXING_UNEXPECTED_FAILURE;
    }

    private static final class IndexingWriteFailure extends RuntimeException {

        private final FailureStage failureStage;
        private final Category category;
        private final RuntimeException originalException;
        private final List<IndexingFailureDiagnostic.RowMetadata> rowMetadata;

        private IndexingWriteFailure(
                FailureStage failureStage,
                Category category,
                RuntimeException originalException,
                List<IndexingFailureDiagnostic.RowMetadata> rowMetadata
        ) {
            super(originalException);
            this.failureStage = failureStage;
            this.category = category;
            this.originalException = originalException;
            this.rowMetadata = rowMetadata;
        }

        private IndexingWriteFailure withRowMetadata(List<IndexingFailureDiagnostic.RowMetadata> rowMetadata) {
            return new IndexingWriteFailure(failureStage, category, originalException, rowMetadata);
        }

        private FailureStage failureStage() {
            return failureStage;
        }

        private Category category() {
            return category;
        }

        private RuntimeException originalException() {
            return originalException;
        }

        private List<IndexingFailureDiagnostic.RowMetadata> rowMetadata() {
            return rowMetadata;
        }
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

    private IndexingPlan toIndexOperations(AssetIndexingSource indexingSource) {
        List<TranscriptSearchIndexClient.TranscriptIndexOperation> operations = new ArrayList<>();
        List<IndexingFailureDiagnostic.RowMetadata> rowMetadata = new ArrayList<>();
        for (AssetTranscriptRowView transcriptRow : indexingSource.transcriptRows()) {
            String documentId = transcriptIndexDocumentMapper.toDocumentId(indexingSource, transcriptRow);
            TranscriptIndexDocument document = transcriptIndexDocumentMapper.toDocument(
                    indexingSource,
                    transcriptRow,
                    AssetStatus.SEARCHABLE
            );
            operations.add(new TranscriptSearchIndexClient.TranscriptIndexOperation(documentId, document));
            String text = document.text();
            rowMetadata.add(new IndexingFailureDiagnostic.RowMetadata(
                    document.segmentIndex(),
                    StringUtils.hasText(text),
                    text == null ? null : text.length()
            ));
        }
        return new IndexingPlan(List.copyOf(operations), List.copyOf(rowMetadata));
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

    private record IndexingPlan(
            List<TranscriptSearchIndexClient.TranscriptIndexOperation> operations,
            List<IndexingFailureDiagnostic.RowMetadata> rowMetadata
    ) {
    }
}
