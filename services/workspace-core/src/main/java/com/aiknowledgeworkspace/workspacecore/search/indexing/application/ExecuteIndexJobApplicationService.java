package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic.RowMetadata;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingAttempt;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexDocument;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexWriteOperation;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexWriter;
import com.aiknowledgeworkspace.workspacecore.search.indexing.transaction.IndexingAttemptTransactionService;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic.Category;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.IndexingFailureDiagnostic.FailureStage;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExecuteIndexJobApplicationService {

    private final IndexingAttemptTransactionService transactionService;
    private final TranscriptIndexWriter transcriptIndexWriter;
    private final TranscriptIndexDocumentMapper transcriptIndexDocumentMapper;

    public ExecuteIndexJobApplicationService(
            IndexingAttemptTransactionService transactionService,
            TranscriptIndexWriter transcriptIndexWriter,
            TranscriptIndexDocumentMapper transcriptIndexDocumentMapper
    ) {
        this.transactionService = transactionService;
        this.transcriptIndexWriter = transcriptIndexWriter;
        this.transcriptIndexDocumentMapper = transcriptIndexDocumentMapper;
    }

    public AssetSearchIndexExecutionResult execute(UUID indexingJobId) {
        IndexingAttempt attempt = transactionService.beginAttempt(indexingJobId);
        if (attempt.result() != null) {
            return attempt.result();
        }

        try {
            writeToElasticsearch(attempt.indexingSource());
        } catch (IndexingWriteFailure failure) {
            recordFailureDiagnostic(attempt, failure);
            throw failure.originalException();
        }
        return transactionService.finalizeSuccessfulAttempt(attempt.indexingJobId());
    }

    public void markJobFailed(UUID indexingJobId, RuntimeException exception) {
        transactionService.markJobFailed(indexingJobId, exception);
    }

    private void writeToElasticsearch(IndexingAssetSource indexingSource) {
        runIndexingStep(
                FailureStage.BEFORE_BULK,
                Category.ELASTICSEARCH_RESPONSE_INVALID,
                transcriptIndexWriter::ensureTranscriptIndexExists
        );
        runIndexingStep(
                FailureStage.BEFORE_BULK,
                Category.ELASTICSEARCH_RESPONSE_INVALID,
                () -> transcriptIndexWriter.deleteTranscriptRowsForAsset(indexingSource.assetId())
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
                    () -> transcriptIndexWriter.indexTranscriptRows(indexingPlan.operations())
            );
            runIndexingStep(
                    FailureStage.AFTER_BULK,
                    Category.ELASTICSEARCH_RESPONSE_INVALID,
                    transcriptIndexWriter::refreshTranscriptIndex
            );
        } catch (IndexingWriteFailure failure) {
            throw failure.withRowMetadata(indexingPlan.rowMetadata());
        }
    }

    private void runIndexingStep(FailureStage stage, Category category, Runnable step) {
        runIndexingStep(stage, category, () -> {
            step.run();
            return null;
        });
    }

    private <T> T runIndexingStep(FailureStage stage, Category category, Supplier<T> step) {
        try {
            return step.get();
        } catch (RuntimeException exception) {
            throw new IndexingWriteFailure(
                    failureStage(exception, stage),
                    failureCategory(exception, category),
                    exception,
                    null
            );
        }
    }

    private void recordFailureDiagnostic(IndexingAttempt attempt, IndexingWriteFailure failure) {
        try {
            List<IndexingFailureDiagnostic.RowMetadata> rowMetadata = failure.rowMetadata();
            if (rowMetadata == null) {
                rowMetadata = attempt.indexingSource().transcriptRows().stream()
                        .map(row -> new IndexingFailureDiagnostic.RowMetadata(
                                row.segmentIndex(),
                                StringUtils.hasText(row.text()),
                                row.text() == null ? null : row.text().length()
                        ))
                        .toList();
            }
            transactionService.persistBestEffortDiagnostic(
                    attempt.indexingJobId(),
                    IndexingFailureDiagnostic.from(
                            rowMetadata,
                            failure.category(),
                            failure.failureStage(),
                            diagnosticExceptionType(failure.originalException())
                    )
            );
        } catch (RuntimeException diagnosticFailure) {
            // Preserve the original indexing failure; diagnostics are best-effort.
        }
    }

    private FailureStage failureStage(RuntimeException exception, FailureStage integrationStage) {
        if (exception instanceof SearchIndexConnectivityException) {
            return FailureStage.TRANSPORT;
        }
        if (exception instanceof SearchIndexOperationException) {
            return integrationStage;
        }
        return FailureStage.UNEXPECTED;
    }

    private Category failureCategory(RuntimeException exception, Category integrationCategory) {
        if (exception instanceof SearchIndexConnectivityException) {
            return Category.ELASTICSEARCH_TRANSPORT_FAILURE;
        }
        if (exception instanceof SearchIndexOperationException) {
            return integrationCategory;
        }
        return Category.INDEXING_UNEXPECTED_FAILURE;
    }

    private String diagnosticExceptionType(RuntimeException exception) {
        if (exception instanceof SearchIndexConnectivityException) {
            return "ElasticsearchConnectivityException";
        }
        if (exception instanceof SearchIndexOperationException) {
            return "ElasticsearchIntegrationException";
        }
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    private IndexingPlan toIndexOperations(IndexingAssetSource indexingSource) {
        List<TranscriptIndexWriteOperation> operations = new ArrayList<>();
        List<IndexingFailureDiagnostic.RowMetadata> rowMetadata = new ArrayList<>();
        for (IndexingTranscriptRow transcriptRow : indexingSource.transcriptRows()) {
            String transcriptRowId = StringUtils.hasText(transcriptRow.id())
                    ? transcriptRow.id()
                    : "segment-" + transcriptRow.segmentIndex();
            TranscriptIndexDocument document = transcriptIndexDocumentMapper.toDocument(indexingSource, transcriptRow);
            operations.add(new TranscriptIndexWriteOperation(
                    indexingSource.assetId() + "-" + transcriptRowId,
                    document
            ));
            String text = document.text();
            rowMetadata.add(new IndexingFailureDiagnostic.RowMetadata(
                    document.segmentIndex(), StringUtils.hasText(text), text == null ? null : text.length()
            ));
        }
        return new IndexingPlan(List.copyOf(operations), List.copyOf(rowMetadata));
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

        private IndexingWriteFailure withRowMetadata(List<IndexingFailureDiagnostic.RowMetadata> metadata) {
            return new IndexingWriteFailure(failureStage, category, originalException, metadata);
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

    private record IndexingPlan(
            List<TranscriptIndexWriteOperation> operations,
            List<IndexingFailureDiagnostic.RowMetadata> rowMetadata
    ) {
    }
}
