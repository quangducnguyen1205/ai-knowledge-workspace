package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.outbox.AssetIndexingRequestedPayload;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TranscriptIndexingServiceTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private AssetPersistenceService assetPersistenceService;

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
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, 0, "Searchable text"));
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);
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
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
    }

    @Test
    void explicitIndexingAlreadyIndexedSameFingerprintIsIdempotentSuccess() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture", AssetStatus.SEARCHABLE);
        ProcessingJob processingJob = processingJob(assetId);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, 0, "Searchable text"));
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        indexingJob.markIndexing();
        indexingJob.markIndexed(Instant.now());

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);
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
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
    }

    @Test
    void explicitIndexingFailureDoesNotRecordDurableJobFailureAndDoesNotMarkSearchable() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture", AssetStatus.SEARCHABLE);
        ProcessingJob processingJob = processingJob(assetId);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, 0, "Searchable text"));
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint-1");
        ElasticsearchIntegrationException failure = new ElasticsearchIntegrationException("bulk failed");

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);
        when(fingerprintService.fingerprint(transcriptRows)).thenReturn("fingerprint-1");
        when(searchIndexRequestService.createExplicitJob(assetId, "fingerprint-1")).thenReturn(indexingJob);
        when(searchIndexingExecutor.indexJob(indexingJob.getId())).thenThrow(failure);

        assertThatThrownBy(() -> transcriptIndexingService().indexAssetTranscript(assetId))
                .isSameAs(failure);

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
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
                assetService,
                assetPersistenceService,
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

    private Asset asset(UUID assetId, UUID workspaceId, String title, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", title, status, new Workspace(workspaceId, "Study Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
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

    private AssetTranscriptRowSnapshot transcriptRow(UUID assetId, int segmentIndex, String text) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                "row-" + segmentIndex,
                "video-1",
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }
}
