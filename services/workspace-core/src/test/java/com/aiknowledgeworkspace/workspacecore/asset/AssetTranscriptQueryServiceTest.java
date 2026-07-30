package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptContext;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetTranscriptQueryServiceTest {

    @Mock
    private AssetStore assetStore;

    @Mock
    private CanonicalTranscriptStore transcriptStore;

    @Mock
    private WorkspaceAccessUseCase workspaceAccess;

    private AssetTranscriptQueryService service;

    @BeforeEach
    void setUp() {
        service = new AssetTranscriptQueryService(assetStore, transcriptStore, workspaceAccess);
    }

    @Test
    void canonicalSnapshotFiltersUnusableRowsAndOrdersSegments() {
        UUID assetId = UUID.randomUUID();
        when(transcriptStore.load(assetId)).thenReturn(List.of(
                row("row-2", 2, "second"),
                row("blank", 1, "  "),
                row("row-1", 1, "first"),
                row("missing-index", null, "ignored")
        ));

        assertThat(service.loadUsableSnapshot(assetId))
                .extracting(AssetTranscriptRowView::id)
                .containsExactly("row-1", "row-2");
    }

    @Test
    void contextIsReadOnlyFromCanonicalProductState() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.SEARCHABLE);
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptStore.load(assetId)).thenReturn(List.of(
                row("row-1", 1, "one"), row("row-2", 2, "two"), row("row-3", 3, "three")
        ));

        AssetTranscriptContext context = service
                .findSearchableTranscriptContext(assetId, workspaceId, "row-2", 1)
                .orElseThrow();

        assertThat(context.assetTitle()).isEqualTo("Lecture");
        assertThat(context.rows()).extracting(AssetTranscriptRowView::id)
                .containsExactly("row-1", "row-2", "row-3");
    }

    @Test
    void nonOwnedAssetDetailsAreHiddenAsNotFound() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetStore.findById(assetId)).thenReturn(Optional.of(
                asset(assetId, workspaceId, AssetStatus.PROCESSING)
        ));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(false);

        assertThatThrownBy(() -> service.getAuthorizedAssetDetails(assetId))
                .isInstanceOf(AssetNotFoundException.class);
    }

    @Test
    void searchableOwnedAssetLoadsTargetedCanonicalContexts() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.SEARCHABLE);
        CanonicalTranscriptContextTarget target = new CanonicalTranscriptContextTarget("row-2", 2);
        CanonicalTranscriptContextRow matched = new CanonicalTranscriptContextRow(
                "row-2", 2, 1000L, 2000L, "canonical", "2026-01-01T00:00:00Z"
        );
        CanonicalTranscriptContextWindow window = new CanonicalTranscriptContextWindow(
                "row-2", 2, matched, List.of(matched)
        );
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);
        when(transcriptStore.loadContextWindows(assetId, List.of(target))).thenReturn(List.of(window));

        assertThat(service.findSearchableTranscriptContexts(
                assetId, workspaceId, List.of(target)
        )).containsExactly(window);
        verify(transcriptStore).loadContextWindows(assetId, List.of(target));
    }

    @Test
    void wrongWorkspaceNonOwnedOrNonSearchableAssetNeverLoadsCanonicalContext() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();

        when(assetStore.findById(assetId)).thenReturn(Optional.of(
                asset(assetId, otherWorkspaceId, AssetStatus.SEARCHABLE)
        ));
        assertThat(service.findSearchableTranscriptContexts(
                assetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget("row-1", 1))
        )).isEmpty();
        verifyNoInteractions(transcriptStore);

        org.mockito.Mockito.reset(assetStore, transcriptStore, workspaceAccess);
        when(assetStore.findById(assetId)).thenReturn(Optional.of(
                asset(assetId, workspaceId, AssetStatus.SEARCHABLE)
        ));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(false);
        assertThat(service.findSearchableTranscriptContexts(
                assetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget("row-1", 1))
        )).isEmpty();
        verifyNoInteractions(transcriptStore);

        org.mockito.Mockito.reset(assetStore, transcriptStore, workspaceAccess);
        when(assetStore.findById(assetId)).thenReturn(Optional.of(
                asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY)
        ));
        assertThat(service.findSearchableTranscriptContexts(
                assetId,
                workspaceId,
                List.of(new CanonicalTranscriptContextTarget("row-1", 1))
        )).isEmpty();
        verifyNoInteractions(transcriptStore);
    }

    private Asset asset(UUID id, UUID workspaceId, AssetStatus status) {
        return Asset.uploaded(
                id, "lecture.mp4", "Lecture", status, workspaceId,
                "workspace-media", "objects/lecture.mp4", "video/mp4", 42L, null
        );
    }

    private AssetTranscriptRowView row(String id, Integer segmentIndex, String text) {
        return new AssetTranscriptRowView(
                id, "video-1", segmentIndex, null, null, text, "2026-01-01T00:00:00Z"
        );
    }
}
