package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingResult;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedPayload;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranscriptIndexingServiceTest {

    @Mock
    private ProcessingRequestApplication processingRequestApplication;

    @Mock
    private IndexingAssetPort indexingAssetPort;

    @Mock
    private TranscriptSnapshotFingerprintService fingerprintService;

    @Mock
    private AssetSearchIndexRequestService searchIndexRequestService;

    @Mock
    private AssetSearchIndexJobRepository searchIndexJobRepository;

    @Mock
    private AssetSearchIndexingExecutor searchIndexingExecutor;

    @Mock
    private AssetIndexingEventParser indexingEventParser;

    @Test
    void explicitIndexingUsesSharedIndexingJobExecutor() {
        UUID assetId = UUID.randomUUID();
        ProcessingJobView processingJob = processingJob(assetId);
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");

        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(indexingAssetPort.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenReturn(new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                AssetSearchIndexJobStatus.INDEXED,
                1
        ));

        ExplicitIndexingResult response = transcriptIndexingService().indexAssetTranscript(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.indexedDocumentCount()).isEqualTo(1);
        verify(searchIndexingExecutor).indexJob(indexingJob.getId());
        verify(indexingAssetPort, never()).markSearchable(assetId);
    }

    @Test
    void explicitIndexingAlreadyIndexedSameFingerprintIsIdempotentSuccess() {
        UUID assetId = UUID.randomUUID();
        ProcessingJobView processingJob = processingJob(assetId);
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        indexingJob.markIndexing();
        indexingJob.markIndexed(Instant.now());

        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(indexingAssetPort.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenReturn(new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                AssetSearchIndexJobStatus.INDEXED,
                0
        ));

        ExplicitIndexingResult response = transcriptIndexingService().indexAssetTranscript(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.indexedDocumentCount()).isEqualTo(1);
        verify(searchIndexingExecutor).indexJob(indexingJob.getId());
        verify(indexingAssetPort, never()).markTranscriptReady(assetId);
        verify(indexingAssetPort, never()).markSearchable(assetId);
    }

    @Test
    void explicitIndexingFailureDoesNotRecordDurableJobFailureAndDoesNotMarkSearchable() {
        UUID assetId = UUID.randomUUID();
        ProcessingJobView processingJob = processingJob(assetId);
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        ElasticsearchIntegrationException failure = new ElasticsearchIntegrationException("bulk failed");

        when(processingRequestApplication.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(indexingAssetPort.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenThrow(failure);

        assertThatThrownBy(() -> transcriptIndexingService().indexAssetTranscript(assetId))
                .isSameAs(failure);

        verify(indexingAssetPort).markTranscriptReady(assetId);
        verify(indexingAssetPort, never()).markSearchable(assetId);
    }

    @Test
    void handleIndexingEventValidatesJobAndExecutesIt() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID indexingJobId = UUID.randomUUID();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(indexingJobId, assetId, "fingerprint-1");
        indexingJob.attachRequestOutboxEvent(eventId);
        AssetIndexingEventEnvelope envelope = envelope(eventId, assetId, indexingJobId, "fingerprint-1");

        when(indexingEventParser.parse("raw-event")).thenReturn(envelope);
        when(searchIndexJobRepository.findById(indexingJobId)).thenReturn(Optional.of(indexingJob));
        when(searchIndexingExecutor.indexJob(indexingJobId)).thenReturn(new AssetSearchIndexExecutionResult(
                indexingJobId,
                AssetSearchIndexJobStatus.INDEXED,
                2
        ));

        AssetIndexingHandleResult result = transcriptIndexingService().handleIndexingEvent("raw-event");

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.indexingJobId()).isEqualTo(indexingJobId);
        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(result.indexedDocumentCount()).isEqualTo(2);
    }

    @Test
    void handleIndexingEventRejectsMismatchedJobFingerprint() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID indexingJobId = UUID.randomUUID();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(indexingJobId, assetId, "fingerprint-1");
        indexingJob.attachRequestOutboxEvent(eventId);

        when(indexingEventParser.parse("raw-event")).thenReturn(envelope(
                eventId,
                assetId,
                indexingJobId,
                "other-fingerprint"
        ));
        when(searchIndexJobRepository.findById(indexingJobId)).thenReturn(Optional.of(indexingJob));

        assertThatThrownBy(() -> transcriptIndexingService().handleIndexingEvent("raw-event"))
                .isInstanceOf(AssetIndexingEventRejectedException.class)
                .hasMessageContaining("snapshot fingerprint");

        verify(searchIndexingExecutor, never()).indexJob(indexingJobId);
    }

    private TranscriptIndexingService transcriptIndexingService() {
        return new TranscriptIndexingService(
                processingRequestApplication,
                indexingAssetPort,
                fingerprintService,
                searchIndexRequestService,
                searchIndexJobRepository,
                searchIndexingExecutor,
                indexingEventParser
        );
    }

    private AssetIndexingEventEnvelope envelope(
            UUID eventId,
            UUID assetId,
            UUID indexingJobId,
            String snapshotFingerprint
    ) {
        return new AssetIndexingEventEnvelope(
                eventId,
                IndexingRequestedEventContract.EVENT_TYPE,
                1,
                IndexingRequestedEventContract.AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                Instant.parse("2026-06-22T00:00:00Z"),
                new IndexingRequestedPayload(assetId, indexingJobId, snapshotFingerprint)
        );
    }

    private IndexingAssetSource source(
            UUID assetId,
            UUID workspaceId,
            String title,
            List<IndexingTranscriptRow> transcriptRows
    ) {
        return new IndexingAssetSource(assetId, workspaceId, title, transcriptRows);
    }

    private ProcessingJobView processingJob(UUID assetId) {
        return new ProcessingJobView(
                UUID.randomUUID(),
                assetId,
                "task-1",
                "video-1",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
    }

    private IndexingTranscriptRow transcriptRow(int segmentIndex, String text) {
        return new IndexingTranscriptRow(
                "row-" + segmentIndex,
                "video-1",
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }
}
