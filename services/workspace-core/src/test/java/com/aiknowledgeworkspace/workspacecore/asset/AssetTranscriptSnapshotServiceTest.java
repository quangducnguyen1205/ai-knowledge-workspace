package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.search.api.IndexingRequestUseCase;
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
    private AssetStore assetStore;

    @Mock
    private CanonicalTranscriptStore transcriptStore;

    @Mock
    private IndexingRequestUseCase indexingRequestApplication;

    @Test
    void canonicalReplacementPersistsRowsAndRequestsAutomaticIndexing() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.PROCESSING);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        List<AssetTranscriptRowView> snapshots = List.of(snapshot("row-1", 0, "canonical"));
        when(transcriptStore.replace(asset.getId(), rows)).thenReturn(snapshots);

        service().replaceCanonicalSnapshot(asset, rows);

        verify(transcriptStore).replace(asset.getId(), rows);
        verify(indexingRequestApplication).requestIndexingIfEnabled(
                org.mockito.Mockito.eq(asset.getId()),
                anyList()
        );
    }

    @Test
    void repeatedIdenticalReplacementPreservesReplaceAndIndexRequestBehavior() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.PROCESSING);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        when(transcriptStore.replace(asset.getId(), rows))
                .thenReturn(List.of(snapshot("row-1", 0, "canonical")));

        service().replaceCanonicalSnapshot(asset, rows);
        service().replaceCanonicalSnapshot(asset, rows);

        verify(transcriptStore, times(2)).replace(asset.getId(), rows);
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

        verify(transcriptStore, never()).replace(
                org.mockito.Mockito.eq(asset.getId()),
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

        verify(assetStore, never()).save(asset);
    }

    @Test
    void successfulProcessingResultDoesNotDowngradeAlreadySearchableAsset() {
        Asset asset = asset(UUID.randomUUID(), AssetStatus.SEARCHABLE);
        List<AssetTranscriptRowInput> rows = List.of(row("row-1", 0, "canonical"));
        when(transcriptStore.replace(asset.getId(), rows))
                .thenReturn(List.of(snapshot("row-1", 0, "canonical")));

        service().applySuccessfulProcessingResult(asset, rows);

        verify(assetStore, never()).save(asset);
    }

    @Test
    void processingFailureUsesTheCanonicalAssetLifecycleOwner() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));

        service().markProcessingFailed(assetId);

        verify(assetStore).save(asset);
    }

    private AssetTranscriptSnapshotService service() {
        return new AssetTranscriptSnapshotService(
                assetStore,
                transcriptStore,
                indexingRequestApplication
        );
    }

    private Asset asset(UUID assetId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, UUID.randomUUID());
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowInput row(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowInput(
                id, "video-1", segmentIndex, null, null, text, "2026-06-26T00:00:00Z"
        );
    }

    private AssetTranscriptRowView snapshot(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowView(
                id, "video-1", segmentIndex, null, null, text, "2026-06-26T00:00:00Z"
        );
    }
}
