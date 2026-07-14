package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshotRepository;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingUploadResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.DirectProcessingJobCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.KafkaProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetPersistenceServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ProcessingRequestApplication processingRequestApplication;

    @Mock
    private AssetTranscriptRowSnapshotRepository assetTranscriptRowSnapshotRepository;

    @Test
    void persistDirectUploadResultCreatesAssetAndProcessingJobWithoutOutboxEvent() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);
        DirectProcessingUploadResult directResult = new DirectProcessingUploadResult(
                "task-1", "video-1", "pending", ProcessingJobStatus.PENDING, AssetStatus.PROCESSING
        );
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
        UUID processingJobId = UUID.randomUUID();
        when(processingRequestApplication.createDirectJob(any())).thenReturn(new ProcessingJobView(
                processingJobId, assetId, "task-1", "video-1", ProcessingJobStatus.PENDING, "pending"
        ));

        AssetUploadResponse response = assetPersistenceService.persistDirectUploadResult(
                assetId,
                "lecture.mp4",
                "Lecture",
                workspace,
                storedObject,
                directResult
        );

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        ArgumentCaptor<DirectProcessingJobCommand> processingJobCaptor =
                ArgumentCaptor.forClass(DirectProcessingJobCommand.class);
        verify(assetRepository).save(assetCaptor.capture());
        verify(processingRequestApplication).createDirectJob(processingJobCaptor.capture());

        assertThat(assetCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(assetCaptor.getValue().getId()).isEqualTo(assetId);
        assertThat(assetCaptor.getValue().getStorageBucket()).isEqualTo("workspace-media");
        assertThat(assetCaptor.getValue().getObjectKey()).isEqualTo(storedObject.objectKey());
        assertThat(assetCaptor.getValue().getContentType()).isEqualTo("video/mp4");
        assertThat(assetCaptor.getValue().getSizeBytes()).isEqualTo(12L);
        assertThat(assetCaptor.getValue().getEtag()).isEqualTo("\"etag-1\"");
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
        assertThat(response.assetId()).isEqualTo(assetId);

        assertThat(processingJobCaptor.getValue().assetId()).isEqualTo(assetId);
        assertThat(processingJobCaptor.getValue().fastapiTaskId()).isEqualTo("task-1");
        assertThat(processingJobCaptor.getValue().fastapiVideoId()).isEqualTo("video-1");

        var inOrder = inOrder(assetRepository, processingRequestApplication);
        inOrder.verify(assetRepository).save(any(Asset.class));
        inOrder.verify(processingRequestApplication).createDirectJob(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void persistKafkaRequestUploadCreatesAssetThenDelegatesAtomicProcessingIntent() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);
        StoredObject storedObject = new StoredObject(
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                12L,
                "video/mp4",
                "\"etag-1\""
        );

        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID processingJobId = UUID.randomUUID();
        when(processingRequestApplication.createKafkaJobAndRequest(any())).thenReturn(new ProcessingJobView(
                processingJobId, assetId, null, null, ProcessingJobStatus.PENDING, "kafka_request_pending"
        ));

        AssetUploadResponse response = assetPersistenceService.persistKafkaRequestUpload(
                assetId,
                "lecture.mp4",
                "Lecture",
                workspace,
                storedObject
        );

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        ArgumentCaptor<KafkaProcessingRequestCommand> processingCommand =
                ArgumentCaptor.forClass(KafkaProcessingRequestCommand.class);
        verify(assetRepository).save(assetCaptor.capture());
        verify(processingRequestApplication).createKafkaJobAndRequest(processingCommand.capture());

        assertThat(assetCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(assetCaptor.getValue().getId()).isEqualTo(assetId);
        assertThat(assetCaptor.getValue().getStatus()).isEqualTo(AssetStatus.PROCESSING);
        assertThat(assetCaptor.getValue().getStorageBucket()).isEqualTo("workspace-media");
        assertThat(assetCaptor.getValue().getObjectKey()).isEqualTo(storedObject.objectKey());
        assertThat(assetCaptor.getValue().getContentType()).isEqualTo("video/mp4");
        assertThat(assetCaptor.getValue().getSizeBytes()).isEqualTo(12L);
        assertThat(assetCaptor.getValue().getEtag()).isEqualTo("\"etag-1\"");
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
        assertThat(response.assetId()).isEqualTo(assetId);

        assertThat(processingCommand.getValue()).isEqualTo(new KafkaProcessingRequestCommand(
                assetId, workspace.getId(), "user-1", "workspace-media", storedObject.objectKey(),
                "lecture.mp4", "video/mp4", 12L
        ));

        var inOrder = inOrder(assetRepository, processingRequestApplication);
        inOrder.verify(assetRepository).save(any(Asset.class));
        inOrder.verify(processingRequestApplication).createKafkaJobAndRequest(any());
    }

    @Test
    void replaceTranscriptSnapshotStoresVerifiedFieldsAndReturnsRowsSortedBySegmentIndex() {
        AssetPersistenceService assetPersistenceService = assetPersistenceService();

        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId);
        List<AssetTranscriptRowInput> upstreamRows = List.of(
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
        assetPersistenceService.deleteAssetRecords(asset);

        var inOrder = inOrder(assetTranscriptRowSnapshotRepository, processingRequestApplication, assetRepository);
        inOrder.verify(assetTranscriptRowSnapshotRepository).deleteByAssetId(assetId);
        inOrder.verify(processingRequestApplication).deleteForAsset(assetId);
        inOrder.verify(assetRepository).delete(asset);
    }

    private AssetPersistenceService assetPersistenceService() {
        return new AssetPersistenceService(
                assetRepository,
                assetTranscriptRowSnapshotRepository,
                processingRequestApplication
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

    private AssetTranscriptRowInput transcriptRow(
            String transcriptRowId,
            String videoId,
            Integer segmentIndex,
            String text
    ) {
        return new AssetTranscriptRowInput(
                transcriptRowId,
                videoId,
                segmentIndex,
                text,
                "2026-04-15T00:00:00Z"
        );
    }
}
