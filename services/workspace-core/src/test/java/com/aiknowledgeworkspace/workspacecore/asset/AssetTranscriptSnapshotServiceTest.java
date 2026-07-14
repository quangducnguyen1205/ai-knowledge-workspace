package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetTranscriptSnapshotServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Mock
    private IndexingRequestApplication indexingRequestApplication;

    @Test
    void canonicalReplacementPersistsRowsAndRequestsAutomaticIndexing() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.PROCESSING);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        List<AssetTranscriptRowSnapshot> snapshots = List.of(snapshot(asset.getId(), "row-1", 0, "canonical"));
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, rows)).thenReturn(snapshots);

        service().replaceCanonicalSnapshot(asset, rows);

        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, rows);
        verify(indexingRequestApplication).requestIndexingIfEnabled(
                org.mockito.Mockito.eq(asset.getId()),
                anyList()
        );
    }

    @Test
    void repeatedIdenticalReplacementPreservesReplaceAndIndexRequestBehavior() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.PROCESSING);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, rows))
                .thenReturn(List.of(snapshot(asset.getId(), "row-1", 0, "canonical")));

        service().replaceCanonicalSnapshot(asset, rows);
        service().replaceCanonicalSnapshot(asset, rows);

        verify(assetPersistenceService, times(2)).replaceTranscriptSnapshot(asset, rows);
        verify(indexingRequestApplication, times(2)).requestIndexingIfEnabled(
                org.mockito.Mockito.eq(asset.getId()),
                anyList()
        );
    }

    @Test
    void invalidCanonicalInputFailsBeforeSnapshotReplacement() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.PROCESSING);

        assertThatThrownBy(() -> service().replaceCanonicalSnapshot(
                asset,
                List.of(row("row-1", null, " "))
        )).isInstanceOf(TranscriptUnavailableException.class);

        verify(assetPersistenceService, never()).replaceTranscriptSnapshot(
                org.mockito.Mockito.eq(asset),
                anyList()
        );
        verify(indexingRequestApplication, never()).requestIndexingIfEnabled(
                org.mockito.Mockito.any(),
                anyList()
        );
    }

    @Test
    void transcriptReadyDoesNotDowngradeAlreadySearchableAsset() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.SEARCHABLE);

        service().markTranscriptReady(asset);

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
    }

    @Test
    void successfulProcessingResultDoesNotDowngradeAlreadySearchableAsset() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.SEARCHABLE);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        when(assetPersistenceService.replaceTranscriptSnapshot(asset, rows))
                .thenReturn(List.of(snapshot(asset.getId(), "row-1", 0, "canonical")));

        service().applySuccessfulProcessingResult(asset, rows);

        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void processingFailureUsesTheCanonicalAssetLifecycleOwner() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        service().markProcessingFailed(assetId);

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.FAILED);
    }

    private AssetTranscriptSnapshotService service() {
        return new AssetTranscriptSnapshotService(
                assetRepository,
                assetPersistenceService,
                indexingRequestApplication
        );
    }

    private Asset asset(UUID assetId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, new Workspace(UUID.randomUUID(), "Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowInput row(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowInput(
                id, "video-1", segmentIndex, text, "2026-06-26T00:00:00Z"
        );
    }

    private AssetTranscriptRowSnapshot snapshot(UUID assetId, String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowSnapshot(
                assetId, id, "video-1", segmentIndex, text, "2026-06-26T00:00:00Z"
        );
    }
}
