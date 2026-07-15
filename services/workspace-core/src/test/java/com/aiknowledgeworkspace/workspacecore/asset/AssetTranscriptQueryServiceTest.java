package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetTranscriptRowSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingCompatibilityGateway;
import com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.DirectProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccessApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetTranscriptQueryServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Mock
    private DirectProcessingCompatibilityGateway compatibilityGateway;

    @Mock
    private WorkspaceAccessApplication workspaceService;

    @Mock
    private IndexingRequestApplication indexingRequestApplication;

    @Test
    void currentIndexingSourceReturnsOrderedUsableCanonicalRows() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of(
                snapshot(assetId, "row-blank", 1, " "),
                snapshot(assetId, "row-2", 2, "second"),
                snapshot(assetId, "row-1", 0, "first")
        ));

        AssetIndexingSource source = queryService().findCurrentIndexingSource(assetId).orElseThrow();

        assertThat(source.transcriptRows()).extracting(AssetTranscriptRowView::id)
                .containsExactly("row-1", "row-2");
        assertThatThrownBy(() -> source.transcriptRows().add(new AssetTranscriptRowView(
                "row-3", "video-1", 3, "third", "2026-06-26T00:00:03Z"
        ))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void searchableTranscriptContextUsesTheCanonicalOrderedSnapshot() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.SEARCHABLE);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of(
                snapshot(assetId, "row-1", 1, "before"),
                snapshot(assetId, "row-2", 2, "hit"),
                snapshot(assetId, "row-3", 3, "after")
        ));

        Optional<AssetTranscriptContext> context = queryService().findSearchableTranscriptContext(
                assetId, workspaceId, "row-2", 1
        );

        assertThat(context).isPresent();
        assertThat(context.orElseThrow().rows()).extracting(AssetTranscriptRowView::id)
                .containsExactly("row-1", "row-2", "row-3");
        assertThat(queryService().findSearchableTranscriptContext(
                assetId, UUID.randomUUID(), "row-2", 1
        )).isEmpty();
    }

    @Test
    void compatibilityFallbackCapturesThroughTheCanonicalSnapshotService() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.PROCESSING);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceService.isOwnedByCurrentUser(asset.getWorkspaceId())).thenReturn(true);
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(compatibilityGateway.transcriptRows("video-1")).thenReturn(List.of(
                new DirectProcessingTranscriptRow("blank", "video-1", 0, " ", "2026-06-26T00:00:00Z"),
                new DirectProcessingTranscriptRow("row-1", "video-1", 1, "usable", "2026-06-26T00:00:01Z")
        ));
        when(assetPersistenceService.replaceTranscriptSnapshot(
                org.mockito.Mockito.eq(asset),
                anyList()
        )).thenReturn(List.of(snapshot(assetId, "row-1", 1, "usable")));
        AssetTranscriptQueryService queryService = queryService();
        AssetTranscriptSnapshotService snapshotService = new AssetTranscriptSnapshotService(
                assetRepository, assetPersistenceService, indexingRequestApplication
        );
        DirectProcessingCompatibilityAdapter adapter = new DirectProcessingCompatibilityAdapter(
                compatibilityGateway, queryService, snapshotService
        );

        AssetIndexingSource source = adapter.loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1");

        assertThat(source.transcriptRows()).extracting(AssetTranscriptRowView::text).containsExactly("usable");
        ArgumentCaptor<List<AssetTranscriptRowInput>> rows = ArgumentCaptor.forClass(List.class);
        verify(assetPersistenceService).replaceTranscriptSnapshot(org.mockito.Mockito.eq(asset), rows.capture());
        assertThat(rows.getValue()).containsExactly(new AssetTranscriptRowInput(
                "row-1", "video-1", 1, "usable", "2026-06-26T00:00:01Z"
        ));
        verify(indexingRequestApplication).requestIndexingIfEnabled(
                org.mockito.Mockito.eq(assetId),
                anyList()
        );
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    private AssetTranscriptQueryService queryService() {
        return new AssetTranscriptQueryService(assetRepository, assetPersistenceService, workspaceService);
    }

    private Asset asset(UUID assetId, UUID workspaceId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, workspaceId);
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowSnapshot snapshot(UUID assetId, String rowId, Integer segmentIndex, String text) {
        return new AssetTranscriptRowSnapshot(
                assetId, rowId, "video-1", segmentIndex, text, "2026-06-26T00:00:00Z"
        );
    }
}
