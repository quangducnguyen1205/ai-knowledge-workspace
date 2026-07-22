package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.service.AssetSearchIndexRequestService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestRow;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxWriter;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.messaging.IndexingRequestedEventCodec;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetSearchIndexRequestServiceTest {

    @Mock
    private SearchIndexJobStore searchIndexJobRepository;

    @Mock
    private OutboxWriter outboxWriter;

    private SearchIndexingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        properties = new SearchIndexingProperties();
        lenient().when(searchIndexJobRepository.save(any(AssetSearchIndexJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(searchIndexJobRepository.findByAssetAndStatuses(any(), any()))
                .thenReturn(List.of());
        lenient().when(searchIndexJobRepository.findByAssetFingerprintAndStatuses(any(), anyString(), any()))
                .thenReturn(List.of());
        lenient().when(searchIndexJobRepository.findLatestIndexed(
                any(),
                anyString()
        )).thenReturn(Optional.empty());
    }

    @Test
    void autoRequestIsDisabledByDefault() {
        UUID assetId = UUID.randomUUID();

        service().requestIndexingIfEnabled(assetId, List.of(row(0, "Text")));

        verify(searchIndexJobRepository, never()).save(any());
        verify(outboxWriter, never()).enqueue(any());
    }

    @Test
    void autoRequestCreatesIndexingJobAndMetadataOnlyOutboxEvent() throws Exception {
        properties.setAutoRequestEnabled(true);
        UUID assetId = UUID.randomUUID();
        List<IndexingRequestRow> rows = List.of(row(0, "Secret transcript text"));

        service().requestIndexingIfEnabled(assetId, rows);

        ArgumentCaptor<AssetSearchIndexJob> jobCaptor = ArgumentCaptor.forClass(AssetSearchIndexJob.class);
        verify(searchIndexJobRepository).save(jobCaptor.capture());
        AssetSearchIndexJob job = jobCaptor.getValue();
        ArgumentCaptor<OutboxDraft> outboxCaptor = ArgumentCaptor.forClass(OutboxDraft.class);
        verify(outboxWriter).enqueue(outboxCaptor.capture());
        OutboxDraft outboxEvent = outboxCaptor.getValue();

        assertThat(job.getStatus()).isEqualTo(AssetSearchIndexJobStatus.PENDING);
        assertThat(job.getAssetId()).isEqualTo(assetId);
        assertThat(job.getRequestOutboxEventId()).isEqualTo(outboxEvent.eventId());
        assertThat(outboxEvent.eventType()).isEqualTo(IndexingRequestedEventContract.EVENT_TYPE);
        assertThat(outboxEvent.eventVersion()).isEqualTo(IndexingRequestedEventContract.EVENT_VERSION);
        assertThat(outboxEvent.aggregateType()).isEqualTo(IndexingRequestedEventContract.AGGREGATE_TYPE);
        assertThat(outboxEvent.aggregateId()).isEqualTo(assetId);
        assertThat(outboxEvent.eventKey()).isEqualTo(assetId.toString());

        JsonNode payload = objectMapper.readTree(outboxEvent.payload());
        assertThat(payload.path("assetId").asText()).isEqualTo(assetId.toString());
        assertThat(payload.path("indexingJobId").asText()).isEqualTo(job.getId().toString());
        assertThat(payload.path("snapshotFingerprint").asText()).isEqualTo(job.getSnapshotFingerprint());
        assertThat(outboxEvent.payload()).doesNotContain("Secret transcript text", "objectKey", "credential", "password");
    }

    @Test
    void duplicateActiveRequestForSameFingerprintDoesNotCreateSecondOutboxIntent() {
        properties.setAutoRequestEnabled(true);
        UUID assetId = UUID.randomUUID();
        List<IndexingRequestRow> rows = List.of(row(0, "Text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(rows);
        AssetSearchIndexJob existingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findByAssetFingerprintAndStatuses(
                assetId,
                fingerprint,
                List.of(AssetSearchIndexJobStatus.PENDING, AssetSearchIndexJobStatus.INDEXING)
        )).thenReturn(List.of(existingJob));

        service().requestIndexingIfEnabled(assetId, rows);

        verify(searchIndexJobRepository, never()).save(any());
        verify(outboxWriter, never()).enqueue(any());
    }

    @Test
    void indexedSameFingerprintDoesNotCreateAnotherExplicitJob() {
        UUID assetId = UUID.randomUUID();
        String fingerprint = "indexed-fingerprint";
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findLatestIndexed(
                assetId,
                fingerprint
        )).thenReturn(Optional.of(indexedJob));

        AssetSearchIndexJob result = service().createExplicitJob(assetId, fingerprint);

        assertThat(result).isSameAs(indexedJob);
        verify(searchIndexJobRepository, never()).findByAssetAndStatuses(any(), any());
        verify(searchIndexJobRepository, never()).save(any(AssetSearchIndexJob.class));
        verify(outboxWriter, never()).enqueue(any());
    }

    @Test
    void indexedSameFingerprintDoesNotCreateAutomaticOutboxIntent() {
        properties.setAutoRequestEnabled(true);
        UUID assetId = UUID.randomUUID();
        List<IndexingRequestRow> rows = List.of(row(0, "Text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(rows);
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findLatestIndexed(
                assetId,
                fingerprint
        )).thenReturn(Optional.of(indexedJob));

        service().requestIndexingIfEnabled(assetId, rows);

        verify(searchIndexJobRepository, never()).save(any());
        verify(outboxWriter, never()).enqueue(any());
    }

    @Test
    void newerSnapshotSupersedesPriorActiveJob() {
        properties.setAutoRequestEnabled(true);
        UUID assetId = UUID.randomUUID();
        AssetSearchIndexJob oldJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "old-fingerprint");

        when(searchIndexJobRepository.findByAssetAndStatuses(
                assetId,
                List.of(AssetSearchIndexJobStatus.PENDING, AssetSearchIndexJobStatus.INDEXING)
        )).thenReturn(List.of(oldJob));

        service().requestIndexingIfEnabled(assetId, List.of(row(0, "New text")));

        assertThat(oldJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        verify(searchIndexJobRepository).save(oldJob);
        verify(outboxWriter).enqueue(any(OutboxDraft.class));
    }

    private AssetSearchIndexRequestService service() {
        return new AssetSearchIndexRequestService(
                properties,
                new TranscriptSnapshotFingerprintService(),
                searchIndexJobRepository,
                new IndexingRequestedEventCodec(objectMapper),
                outboxWriter
        );
    }

    private IndexingRequestRow row(int segmentIndex, String text) {
        return new IndexingRequestRow(
                "row-" + segmentIndex,
                "video-1",
                segmentIndex,
                null,
                null,
                text,
                "2026-06-22T00:00:00Z"
        );
    }
}
