package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingRequestApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccessApplication;
import java.time.Instant;
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
class AssetQueryApplicationServiceTest {

    @Mock
    private AssetStore assetStore;

    @Mock
    private ProcessingRequestApplication processingRequests;

    @Mock
    private AssetTranscriptQueryService transcripts;

    @Mock
    private WorkspaceAccessApplication workspaceAccess;

    private AssetQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AssetQueryApplicationService(assetStore, processingRequests, transcripts, workspaceAccess);
    }

    @Test
    void statusQueryReadsCanonicalStateWithoutPollingOrMutation() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY, Instant.parse("2026-01-01T00:00:00Z"));
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);
        when(processingRequests.findByAssetId(assetId)).thenReturn(Optional.of(
                new ProcessingJobView(jobId, assetId, ProcessingJobStatus.SUCCEEDED, "completed")
        ));

        AssetStatusView result = service.getAssetStatus(assetId);

        assertThat(result).isEqualTo(new AssetStatusView(
                assetId, jobId, AssetStatus.TRANSCRIPT_READY, ProcessingJobStatus.SUCCEEDED
        ));
        verifyNoInteractions(transcripts);
    }

    @Test
    void nonOwnedAssetIsHiddenAsNotFound() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetStore.findById(assetId)).thenReturn(Optional.of(
                asset(assetId, workspaceId, AssetStatus.PROCESSING, Instant.now())
        ));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(false);

        assertThatThrownBy(() -> service.getAsset(assetId))
                .isInstanceOf(AssetNotFoundException.class);
    }

    @Test
    void listAppliesStableOrderingFilteringAndPaginationInApplicationLayer() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, "owner-1"));
        Asset older = asset(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                workspaceId,
                AssetStatus.SEARCHABLE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Asset newer = asset(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                workspaceId,
                AssetStatus.SEARCHABLE,
                Instant.parse("2026-01-02T00:00:00Z")
        );
        when(assetStore.findByWorkspaceId(workspaceId)).thenReturn(List.of(older, newer));

        AssetPage page = service.listAssets(workspaceId, 0, 1, AssetStatus.SEARCHABLE);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(newer.getId());
    }

    @Test
    void successfulJobWithoutCanonicalRowsIsNotSilentlyRefreshedFromFastApi() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.TRANSCRIPT_READY, Instant.now());
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);
        when(processingRequests.findByAssetId(assetId)).thenReturn(Optional.of(
                new ProcessingJobView(UUID.randomUUID(), assetId, ProcessingJobStatus.SUCCEEDED, "completed")
        ));
        when(transcripts.loadUsableSnapshot(assetId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getAssetTranscript(assetId))
                .isInstanceOf(TranscriptUnavailableException.class)
                .hasMessageContaining("Canonical transcript is unavailable");
    }

    private Asset asset(UUID id, UUID workspaceId, AssetStatus status, Instant createdAt) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, workspaceId);
        ReflectionTestUtils.setField(asset, "id", id);
        ReflectionTestUtils.setField(asset, "createdAt", createdAt);
        ReflectionTestUtils.setField(asset, "updatedAt", createdAt);
        return asset;
    }
}
