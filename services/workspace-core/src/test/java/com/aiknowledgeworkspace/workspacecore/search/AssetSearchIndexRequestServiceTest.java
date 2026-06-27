package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetSearchIndexRequestServiceTest {

    @Mock
    private AssetSearchIndexJobRepository searchIndexJobRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private SearchIndexingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        properties = new SearchIndexingProperties();
        lenient().when(searchIndexJobRepository.save(any(AssetSearchIndexJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(searchIndexJobRepository.findByAssetIdAndStatusIn(any(), any()))
                .thenReturn(List.of());
        lenient().when(searchIndexJobRepository.findByAssetIdAndSnapshotFingerprintAndStatusIn(any(), anyString(), any()))
                .thenReturn(List.of());
        lenient().when(searchIndexJobRepository.findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
                any(),
                anyString(),
                any()
        )).thenReturn(Optional.empty());
    }

    @Test
    void autoRequestIsDisabledByDefault() {
        Asset asset = asset(UUID.randomUUID());

        Optional<AssetSearchIndexJob> result = service().requestIndexingIfEnabled(asset.getId(), List.of(row(0, "Text")));

        assertThat(result).isEmpty();
        verify(searchIndexJobRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void autoRequestCreatesIndexingJobAndMetadataOnlyOutboxEvent() throws Exception {
        properties.setAutoRequestEnabled(true);
        Asset asset = asset(UUID.randomUUID());
        List<AssetTranscriptRowView> rows = List.of(row(0, "Secret transcript text"));

        AssetSearchIndexJob job = service().requestIndexingIfEnabled(asset.getId(), rows).orElseThrow();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertThat(job.getStatus()).isEqualTo(AssetSearchIndexJobStatus.PENDING);
        assertThat(job.getAssetId()).isEqualTo(asset.getId());
        assertThat(job.getRequestOutboxEventId()).isEqualTo(outboxEvent.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEventFactory.ASSET_INDEXING_REQUESTED);
        assertThat(outboxEvent.getEventVersion()).isEqualTo(1);
        assertThat(outboxEvent.getAggregateType()).isEqualTo(OutboxEventFactory.ASSET_INDEXING_AGGREGATE_TYPE);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(asset.getId());
        assertThat(outboxEvent.getEventKey()).isEqualTo(asset.getId().toString());

        JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
        assertThat(payload.path("assetId").asText()).isEqualTo(asset.getId().toString());
        assertThat(payload.path("indexingJobId").asText()).isEqualTo(job.getId().toString());
        assertThat(payload.path("snapshotFingerprint").asText()).isEqualTo(job.getSnapshotFingerprint());
        assertThat(outboxEvent.getPayload()).doesNotContain("Secret transcript text", "objectKey", "credential", "password");
    }

    @Test
    void duplicateActiveRequestForSameFingerprintDoesNotCreateSecondOutboxIntent() {
        properties.setAutoRequestEnabled(true);
        Asset asset = asset(UUID.randomUUID());
        List<AssetTranscriptRowView> rows = List.of(row(0, "Text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(rows);
        AssetSearchIndexJob existingJob = new AssetSearchIndexJob(UUID.randomUUID(), asset.getId(), fingerprint);

        when(searchIndexJobRepository.findByAssetIdAndSnapshotFingerprintAndStatusIn(
                asset.getId(),
                fingerprint,
                List.of(AssetSearchIndexJobStatus.PENDING, AssetSearchIndexJobStatus.INDEXING)
        )).thenReturn(List.of(existingJob));

        AssetSearchIndexJob result = service().requestIndexingIfEnabled(asset.getId(), rows).orElseThrow();

        assertThat(result).isSameAs(existingJob);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void indexedSameFingerprintDoesNotCreateAnotherExplicitJob() {
        UUID assetId = UUID.randomUUID();
        String fingerprint = "indexed-fingerprint";
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
                assetId,
                fingerprint,
                AssetSearchIndexJobStatus.INDEXED
        )).thenReturn(Optional.of(indexedJob));

        AssetSearchIndexJob result = service().createExplicitJob(assetId, fingerprint);

        assertThat(result).isSameAs(indexedJob);
        verify(searchIndexJobRepository, never()).findByAssetIdAndStatusIn(any(), any());
        verify(searchIndexJobRepository, never()).save(any(AssetSearchIndexJob.class));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void indexedSameFingerprintDoesNotCreateAutomaticOutboxIntent() {
        properties.setAutoRequestEnabled(true);
        Asset asset = asset(UUID.randomUUID());
        List<AssetTranscriptRowView> rows = List.of(row(0, "Text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(rows);
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(UUID.randomUUID(), asset.getId(), fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
                asset.getId(),
                fingerprint,
                AssetSearchIndexJobStatus.INDEXED
        )).thenReturn(Optional.of(indexedJob));

        AssetSearchIndexJob result = service().requestIndexingIfEnabled(asset.getId(), rows).orElseThrow();

        assertThat(result).isSameAs(indexedJob);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void newerSnapshotSupersedesPriorActiveJob() {
        properties.setAutoRequestEnabled(true);
        Asset asset = asset(UUID.randomUUID());
        AssetSearchIndexJob oldJob = new AssetSearchIndexJob(UUID.randomUUID(), asset.getId(), "old-fingerprint");

        when(searchIndexJobRepository.findByAssetIdAndStatusIn(
                asset.getId(),
                List.of(AssetSearchIndexJobStatus.PENDING, AssetSearchIndexJobStatus.INDEXING)
        )).thenReturn(List.of(oldJob));

        service().requestIndexingIfEnabled(asset.getId(), List.of(row(0, "New text")));

        assertThat(oldJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        verify(searchIndexJobRepository).save(oldJob);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    private AssetSearchIndexRequestService service() {
        return new AssetSearchIndexRequestService(
                properties,
                new TranscriptSnapshotFingerprintService(),
                searchIndexJobRepository,
                new OutboxEventFactory(objectMapper),
                outboxEventRepository
        );
    }

    private Asset asset(UUID assetId) {
        Asset asset = new Asset("lecture.mp4", "Lecture", AssetStatus.TRANSCRIPT_READY, new Workspace(UUID.randomUUID(), "Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowView row(int segmentIndex, String text) {
        return new AssetTranscriptRowView(
                "row-" + segmentIndex,
                "video-1",
                segmentIndex,
                text,
                "2026-06-22T00:00:00Z"
        );
    }
}
