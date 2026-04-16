package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
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

    @Test
    void replaceTranscriptSnapshotStoresVerifiedFieldsAndReturnsRowsSortedBySegmentIndex() {
        AssetPersistenceService assetPersistenceService = new AssetPersistenceService(
                assetRepository,
                processingJobRepository,
                assetTranscriptRowSnapshotRepository
        );

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
        AssetPersistenceService assetPersistenceService = new AssetPersistenceService(
                assetRepository,
                processingJobRepository,
                assetTranscriptRowSnapshotRepository
        );

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
        AssetPersistenceService assetPersistenceService = new AssetPersistenceService(
                assetRepository,
                processingJobRepository,
                assetTranscriptRowSnapshotRepository
        );

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
