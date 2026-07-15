package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.indexing.application.AssetSearchIndexExecutionResult;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.ExecuteIndexJobApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.TranscriptIndexDocumentMapper;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexWriteOperation;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexWriter;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;
import com.aiknowledgeworkspace.workspacecore.search.indexing.transaction.IndexingAttemptTransactionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class IndexingTransactionBoundaryTest {

    @Test
    void indexingKeepsBeginAndFinalizeTransactionsAroundAnExternalWrite() {
        AssetSearchIndexJobRepository repository = mock(AssetSearchIndexJobRepository.class);
        IndexingAssetPort assetPort = mock(IndexingAssetPort.class);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        RecordingWriter writer = new RecordingWriter(transactionManager);
        TranscriptSnapshotFingerprintService fingerprintService = new TranscriptSnapshotFingerprintService();

        UUID assetId = UUID.randomUUID();
        IndexingAssetSource source = new IndexingAssetSource(
                assetId,
                UUID.randomUUID(),
                "Transaction boundary",
                List.of(new IndexingTranscriptRow(
                        "row-1", "video-1", 0, "Indexed outside the transaction", "2026-07-13T00:00:00Z"
                ))
        );
        AssetSearchIndexJob job = new AssetSearchIndexJob(
                UUID.randomUUID(),
                assetId,
                fingerprintService.fingerprint(source.transcriptRows())
        );
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(assetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(source));

        ExecuteIndexJobApplicationService applicationService = new ExecuteIndexJobApplicationService(
                new IndexingAttemptTransactionService(repository, assetPort, fingerprintService, transactionManager),
                writer,
                new TranscriptIndexDocumentMapper()
        );

        AssetSearchIndexExecutionResult result = applicationService.execute(job.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(transactionManager.beginCount).isEqualTo(2);
        assertThat(transactionManager.commitCount).isEqualTo(2);
        assertThat(writer.operations)
                .containsExactly("ensure-index", "delete-asset", "bulk-index", "refresh-index");
    }

    private static final class RecordingWriter implements TranscriptIndexWriter {

        private final RecordingTransactionManager transactionManager;
        private final List<String> operations = new ArrayList<>();

        private RecordingWriter(RecordingTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        @Override
        public void ensureTranscriptIndexExists() {
            recordOutsideTransaction("ensure-index");
        }

        @Override
        public void deleteTranscriptRowsForAsset(UUID assetId) {
            recordOutsideTransaction("delete-asset");
        }

        @Override
        public void indexTranscriptRows(List<TranscriptIndexWriteOperation> operations) {
            assertThat(operations).hasSize(1);
            recordOutsideTransaction("bulk-index");
        }

        @Override
        public void refreshTranscriptIndex() {
            recordOutsideTransaction("refresh-index");
        }

        private void recordOutsideTransaction(String operation) {
            assertThat(transactionManager.active)
                    .as("Elasticsearch operation %s must remain outside the database transaction", operation)
                    .isFalse();
            operations.add(operation);
        }
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private boolean active;
        private int beginCount;
        private int commitCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active = true;
            beginCount++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            active = false;
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            active = false;
        }
    }
}
