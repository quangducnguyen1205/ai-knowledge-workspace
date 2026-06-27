package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.AssetReadService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.outbox.AssetIndexingRequestedPayload;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
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
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private AssetReadService assetReadService;

    @Mock
    private AssetSearchabilityService assetSearchabilityService;

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
        ProcessingJob processingJob = processingJob(assetId);
        AssetIndexingSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<AssetTranscriptRowView> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");

        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetReadService.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenReturn(new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                AssetSearchIndexJobStatus.INDEXED,
                1
        ));

        AssetIndexResponse response = transcriptIndexingService().indexAssetTranscript(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.assetStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        assertThat(response.indexedDocumentCount()).isEqualTo(1);
        verify(searchIndexingExecutor).indexJob(indexingJob.getId());
        verify(assetSearchabilityService, never()).markSearchable(assetId);
    }

    @Test
    void explicitIndexingAlreadyIndexedSameFingerprintIsIdempotentSuccess() {
        UUID assetId = UUID.randomUUID();
        ProcessingJob processingJob = processingJob(assetId);
        AssetIndexingSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<AssetTranscriptRowView> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        indexingJob.markIndexing();
        indexingJob.markIndexed(Instant.now());

        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetReadService.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenReturn(new AssetSearchIndexExecutionResult(
                indexingJob.getId(),
                AssetSearchIndexJobStatus.INDEXED,
                0
        ));

        AssetIndexResponse response = transcriptIndexingService().indexAssetTranscript(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.assetStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        assertThat(response.indexedDocumentCount()).isEqualTo(1);
        verify(searchIndexingExecutor).indexJob(indexingJob.getId());
        verify(assetSearchabilityService, never()).markTranscriptReady(assetId);
        verify(assetSearchabilityService, never()).markSearchable(assetId);
    }

    @Test
    void explicitIndexingFailureDoesNotRecordDurableJobFailureAndDoesNotMarkSearchable() {
        UUID assetId = UUID.randomUUID();
        ProcessingJob processingJob = processingJob(assetId);
        AssetIndexingSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture", List.of(
                transcriptRow(0, "Searchable text")
        ));
        List<AssetTranscriptRowView> transcriptRows = indexingSource.transcriptRows();
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        ElasticsearchIntegrationException failure = new ElasticsearchIntegrationException("bulk failed");

        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetReadService.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1"))
                .thenReturn(indexingSource);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenThrow(failure);

        assertThatThrownBy(() -> transcriptIndexingService().indexAssetTranscript(assetId))
                .isSameAs(failure);

        verify(assetSearchabilityService).markTranscriptReady(assetId);
        verify(assetSearchabilityService, never()).markSearchable(assetId);
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
                processingJobRepository,
                assetReadService,
                assetSearchabilityService,
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
                OutboxEventFactory.ASSET_INDEXING_REQUESTED,
                1,
                OutboxEventFactory.ASSET_INDEXING_AGGREGATE_TYPE,
                assetId,
                assetId.toString(),
                Instant.parse("2026-06-22T00:00:00Z"),
                new AssetIndexingRequestedPayload(assetId, indexingJobId, snapshotFingerprint)
        );
    }

    private AssetIndexingSource source(
            UUID assetId,
            UUID workspaceId,
            String title,
            List<AssetTranscriptRowView> transcriptRows
    ) {
        return new AssetIndexingSource(assetId, workspaceId, title, transcriptRows);
    }

    private ProcessingJob processingJob(UUID assetId) {
        return new ProcessingJob(
                assetId,
                "task-1",
                "video-1",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
    }

    private AssetTranscriptRowView transcriptRow(int segmentIndex, String text) {
        return new AssetTranscriptRowView(
                "row-" + segmentIndex,
                "video-1",
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }
}
