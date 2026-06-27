package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
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
class AssetReadServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Mock
    private FastApiProcessingClient fastApiProcessingClient;

    @Mock
    private WorkspaceService workspaceService;

    @Test
    void currentIndexingSourceReturnsImmutableApplicationRowsWithoutJpaEntities() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of(
                snapshot(assetId, "row-1", 0, "Searchable transcript"),
                snapshot(assetId, "row-blank", 1, " ")
        ));

        AssetIndexingSource source = service().findCurrentIndexingSource(assetId).orElseThrow();

        assertThat(source.assetId()).isEqualTo(assetId);
        assertThat(source.workspaceId()).isEqualTo(workspaceId);
        assertThat(source.assetTitle()).isEqualTo("Lecture");
        assertThat(source.transcriptRows()).containsExactly(new AssetTranscriptRowView(
                "row-1",
                "video-1",
                0,
                "Searchable transcript",
                "2026-06-26T00:00:00Z"
        ));
        assertThatThrownBy(() -> source.transcriptRows().add(new AssetTranscriptRowView(
                "row-2",
                "video-1",
                1,
                "extra",
                "2026-06-26T00:00:01Z"
        ))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void searchableTranscriptContextRespectsWorkspaceAndAssetSearchabilityGate() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.SEARCHABLE);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of(
                snapshot(assetId, "row-1", 1, "before"),
                snapshot(assetId, "row-2", 2, "hit"),
                snapshot(assetId, "row-3", 3, "after")
        ));

        Optional<AssetTranscriptContext> context = service().findSearchableTranscriptContext(
                assetId,
                workspaceId,
                "row-2",
                1
        );

        assertThat(context).isPresent();
        assertThat(context.orElseThrow().rows()).extracting(AssetTranscriptRowView::id)
                .containsExactly("row-1", "row-2", "row-3");
        assertThat(service().findSearchableTranscriptContext(assetId, UUID.randomUUID(), "row-2", 1)).isEmpty();

        Asset nonSearchable = asset(UUID.randomUUID(), workspaceId, AssetStatus.TRANSCRIPT_READY);
        when(assetRepository.findById(nonSearchable.getId())).thenReturn(Optional.of(nonSearchable));
        assertThat(service().findSearchableTranscriptContext(nonSearchable.getId(), workspaceId, "row-2", 1))
                .isEmpty();
    }

    @Test
    void authorizedIndexingSourceCapturesFallbackTranscriptThroughAssetInputContract() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.PROCESSING);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceService.isOwnedByCurrentUser(asset.getWorkspace())).thenReturn(true);
        when(assetPersistenceService.loadTranscriptSnapshot(assetId)).thenReturn(List.of());
        when(fastApiProcessingClient.getTranscript("video-1")).thenReturn(List.of(
                new FastApiTranscriptRowResponse("blank", "video-1", 0, " ", "2026-06-26T00:00:00Z"),
                new FastApiTranscriptRowResponse("row-1", "video-1", 1, "usable", "2026-06-26T00:00:01Z")
        ));
        when(assetPersistenceService.replaceTranscriptSnapshot(
                org.mockito.Mockito.eq(asset),
                anyList()
        )).thenReturn(List.of(snapshot(assetId, "row-1", 1, "usable")));

        AssetIndexingSource source = service().loadAuthorizedIndexingSourceForCompletedProcessing(assetId, "video-1");

        assertThat(source.transcriptRows()).extracting(AssetTranscriptRowView::text).containsExactly("usable");
        ArgumentCaptor<List<AssetTranscriptRowInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetPersistenceService).replaceTranscriptSnapshot(org.mockito.Mockito.eq(asset), captor.capture());
        assertThat(captor.getValue()).containsExactly(new AssetTranscriptRowInput(
                "row-1",
                "video-1",
                1,
                "usable",
                "2026-06-26T00:00:01Z"
        ));
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    private AssetReadService service() {
        return new AssetReadService(assetRepository, assetPersistenceService, fastApiProcessingClient, workspaceService);
    }

    private Asset asset(UUID assetId, UUID workspaceId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, new Workspace(workspaceId, "Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowSnapshot snapshot(UUID assetId, String rowId, Integer segmentIndex, String text) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                rowId,
                "video-1",
                segmentIndex,
                text,
                "2026-06-26T00:00:00Z"
        );
    }
}
