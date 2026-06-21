package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEvent;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventFactory;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventRepository;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiUploadResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetPersistenceServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistUploadResultCreatesAssetProcessingJobAndOutboxEvent() throws Exception {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);
        FastApiUploadResponse upstreamResponse = new FastApiUploadResponse("task-1", "pending", "video-1");
        StoredObject storedObject = new StoredObject(
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                12L,
                "video/mp4",
                "\"etag-1\""
        );

        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            return asset;
        });
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetUploadResponse response = assetPersistenceService.persistUploadResult(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.PROCESSING,
                ProcessingJobStatus.PENDING,
                workspace,
                storedObject,
                upstreamResponse
        );

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        ArgumentCaptor<ProcessingJob> processingJobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(assetRepository).save(assetCaptor.capture());
        verify(processingJobRepository).save(processingJobCaptor.capture());
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        assertThat(assetCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(assetCaptor.getValue().getId()).isEqualTo(assetId);
        assertThat(assetCaptor.getValue().getStorageBucket()).isEqualTo("workspace-media");
        assertThat(assetCaptor.getValue().getObjectKey()).isEqualTo(storedObject.objectKey());
        assertThat(assetCaptor.getValue().getContentType()).isEqualTo("video/mp4");
        assertThat(assetCaptor.getValue().getSizeBytes()).isEqualTo(12L);
        assertThat(assetCaptor.getValue().getEtag()).isEqualTo("\"etag-1\"");
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
        assertThat(response.assetId()).isEqualTo(assetId);

        assertThat(processingJobCaptor.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(processingJobCaptor.getValue().getFastapiTaskId()).isEqualTo("task-1");
        assertThat(processingJobCaptor.getValue().getFastapiVideoId()).isEqualTo("video-1");

        OutboxEvent outboxEvent = outboxEventCaptor.getValue();
        assertThat(outboxEvent.getId()).isNotNull();
        assertThat(processingJobCaptor.getValue().getProcessingRequestEventId()).isEqualTo(outboxEvent.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEventFactory.ASSET_PROCESSING_REQUESTED);
        assertThat(outboxEvent.getEventVersion()).isEqualTo(OutboxEventFactory.ASSET_PROCESSING_REQUESTED_VERSION);
        assertThat(outboxEvent.getAggregateType()).isEqualTo(OutboxEventFactory.ASSET_AGGREGATE_TYPE);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(assetId);
        assertThat(outboxEvent.getEventKey()).isEqualTo(assetId.toString());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getAttemptCount()).isZero();

        JsonNode payload = objectMapper.readTree(outboxEvent.getPayload());
        assertThat(payload.path("assetId").asText()).isEqualTo(assetId.toString());
        assertThat(payload.path("workspaceId").asText()).isEqualTo(workspace.getId().toString());
        assertThat(payload.path("ownerId").asText()).isEqualTo("user-1");
        assertThat(payload.path("storageBucket").asText()).isEqualTo("workspace-media");
        assertThat(payload.path("objectKey").asText()).isEqualTo(storedObject.objectKey());
        assertThat(payload.path("originalFilename").asText()).isEqualTo("lecture.mp4");
        assertThat(payload.path("contentType").asText()).isEqualTo("video/mp4");
        assertThat(payload.path("sizeBytes").asLong()).isEqualTo(12L);
        assertThat(payload.path("requestedAt").asText()).isNotBlank();

        InOrder inOrder = inOrder(assetRepository, processingJobRepository, outboxEventRepository);
        inOrder.verify(assetRepository).save(any(Asset.class));
        inOrder.verify(processingJobRepository).save(any(ProcessingJob.class));
        inOrder.verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void replaceTranscriptSnapshotStoresVerifiedFieldsAndReturnsRowsSortedBySegmentIndex() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId);
        List<FastApiTranscriptRowResponse> upstreamRows = List.of(
                transcriptRow("row-2", "video-1", 2, "second"),
                transcriptRow("row-1", "video-1", 1, "first")
        );

        when(assetTranscriptRowSnapshotRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<AssetTranscriptRowSnapshot> savedRows =
                assetPersistenceService.replaceTranscriptSnapshot(asset, upstreamRows);

        ArgumentCaptor<List<AssetTranscriptRowSnapshot>> snapshotsCaptor = ArgumentCaptor.forClass(List.class);
        verify(assetTranscriptRowSnapshotRepository).deleteByAssetId(assetId);
        verify(assetTranscriptRowSnapshotRepository).saveAll(snapshotsCaptor.capture());

        assertThat(savedRows).extracting(AssetTranscriptRowSnapshot::getSegmentIndex)
                .containsExactly(1, 2);
        assertThat(snapshotsCaptor.getValue()).hasSize(2);
        assertThat(snapshotsCaptor.getValue()).extracting(AssetTranscriptRowSnapshot::getAssetId)
                .containsOnly(assetId);
        assertThat(snapshotsCaptor.getValue()).extracting(AssetTranscriptRowSnapshot::getTranscriptRowId)
                .containsExactly("row-2", "row-1");
        assertThat(snapshotsCaptor.getValue()).extracting(AssetTranscriptRowSnapshot::getVideoId)
                .containsOnly("video-1");
        assertThat(snapshotsCaptor.getValue()).extracting(AssetTranscriptRowSnapshot::getText)
                .containsExactly("second", "first");
        assertThat(snapshotsCaptor.getValue()).extracting(AssetTranscriptRowSnapshot::getCreatedAt)
                .containsOnly("2026-04-15T00:00:00Z");
    }

    @Test
    void loadTranscriptSnapshotReturnsRowsSortedBySegmentIndex() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        when(assetTranscriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(List.of(
                snapshot(assetId, "row-3", "video-1", 3, "third"),
                snapshot(assetId, "row-1", "video-1", 1, "first"),
                snapshot(assetId, "row-2", "video-1", 2, "second")
        ));

        List<AssetTranscriptRowSnapshot> result = assetPersistenceService.loadTranscriptSnapshot(assetId);

        assertThat(result).extracting(AssetTranscriptRowSnapshot::getSegmentIndex)
                .containsExactly(1, 2, 3);
    }

    @Test
    void deleteAssetRecordsDeletesTranscriptRowsBeforeProcessingJobAndAsset() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId);
        ProcessingJob processingJob = new ProcessingJob(
                assetId,
                "task-1",
                "video-1",
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );

        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));

        assetPersistenceService.deleteAssetRecords(asset);

        InOrder inOrder = inOrder(assetTranscriptRowSnapshotRepository, processingJobRepository, assetRepository);
        inOrder.verify(assetTranscriptRowSnapshotRepository).deleteByAssetId(assetId);
        inOrder.verify(processingJobRepository).findByAssetId(assetId);
        inOrder.verify(processingJobRepository).delete(processingJob);
        inOrder.verify(assetRepository).delete(asset);
    }

    private AssetPersistenceService assetPersistenceService() {
        return new AssetPersistenceService(
                assetRepository,
                processingJobRepository,
                assetTranscriptRowSnapshotRepository,
                outboxEventRepository,
                new OutboxEventFactory(objectMapper)
        );
    }

    private Asset asset(UUID assetId) {
        Asset asset = new Asset(
                "lecture.mp4",
                "Lecture",
                AssetStatus.TRANSCRIPT_READY,
                new Workspace(UUID.randomUUID(), "Workspace")
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowSnapshot snapshot(
            UUID assetId,
            String transcriptRowId,
            String videoId,
            Integer segmentIndex,
            String text
    ) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                transcriptRowId,
                videoId,
                segmentIndex,
                text,
                "2026-04-15T00:00:00Z"
        );
    }

    private FastApiTranscriptRowResponse transcriptRow(
            String transcriptRowId,
            String videoId,
            Integer segmentIndex,
            String text
    ) {
        return new FastApiTranscriptRowResponse(
                transcriptRowId,
                videoId,
                segmentIndex,
                text,
                "2026-04-15T00:00:00Z"
        );
    }
}
