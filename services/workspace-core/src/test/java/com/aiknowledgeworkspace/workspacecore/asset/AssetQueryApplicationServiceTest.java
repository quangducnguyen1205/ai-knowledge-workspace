package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.TranscriptUnavailableException;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetQueryApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobView;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestUseCase;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetQueryApplicationServiceTest {

    @Mock
    private AssetStore assetStore;

    @Mock
    private ProcessingRequestUseCase processingRequests;

    @Mock
    private AssetTranscriptQueryService transcripts;

    @Mock
    private WorkspaceAccessUseCase workspaceAccess;

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
                assetId, jobId, AssetStatus.TRANSCRIPT_READY, ProcessingJobStatus.SUCCEEDED, null
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

    @ParameterizedTest
    @ValueSource(strings = {
            "YOUTUBE_UNAVAILABLE",
            "YOUTUBE_LIVE_NOT_SUPPORTED",
            "YOUTUBE_DURATION_LIMIT_EXCEEDED",
            "YOUTUBE_SIZE_LIMIT_EXCEEDED",
            "YOUTUBE_ACQUISITION_TIMEOUT",
            "YOUTUBE_ACQUISITION_FAILED"
    })
    void failedStatusSurfacesTheSupportedSafeIntegrationFailureCodes(String failureCode) {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.FAILED, Instant.now());
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);
        when(processingRequests.findByAssetId(assetId)).thenReturn(Optional.of(
                new ProcessingJobView(
                        jobId,
                        assetId,
                        ProcessingJobStatus.FAILED,
                        failureCode.toLowerCase(java.util.Locale.ROOT)
                )
        ));

        AssetStatusView result = service.getAssetStatus(assetId);

        assertThat(result.failureCode()).isEqualTo(failureCode);
    }

    @Test
    void failedStatusDoesNotExposeUnsafeUpstreamDiagnosticText() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, AssetStatus.FAILED, Instant.now());
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);
        when(processingRequests.findByAssetId(assetId)).thenReturn(Optional.of(
                new ProcessingJobView(
                        UUID.randomUUID(),
                        assetId,
                        ProcessingJobStatus.FAILED,
                        "provider stderr with raw url"
                )
        ));

        assertThat(service.getAssetStatus(assetId).failureCode()).isEqualTo("PROCESSING_FAILED");
    }

    @Test
    void listDelegatesOrderingFilteringAndPaginationToTheStore() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, "owner-1"));
        Asset newer = asset(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                workspaceId,
                AssetStatus.SEARCHABLE,
                Instant.parse("2026-01-02T00:00:00Z")
        );
        // The database returns the page; the service must not re-slice or re-sort it.
        when(assetStore.countWorkspaceAssets(workspaceId, AssetStatus.SEARCHABLE)).thenReturn(2L);
        when(assetStore.findWorkspacePage(workspaceId, AssetStatus.SEARCHABLE, 0, 1))
                .thenReturn(List.of(newer));

        AssetPage page = service.listAssets(workspaceId, 0, 1, AssetStatus.SEARCHABLE);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(newer.getId());
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo(AssetSourceType.UPLOAD);
            assertThat(item.youtubeVideoId()).isNull();
        });
        verify(assetStore, never()).findByWorkspaceId(workspaceId);
    }

    @Test
    void listAsksTheStoreForTheRequestedPageAndFilterUnchanged() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, "owner-1"));
        when(assetStore.countWorkspaceAssets(workspaceId, null)).thenReturn(97L);
        when(assetStore.findWorkspacePage(workspaceId, null, 3, 20)).thenReturn(List.of());

        AssetPage page = service.listAssets(workspaceId, 3, 20, null);

        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(97);
        assertThat(page.totalPages()).isEqualTo(5);
        assertThat(page.hasNext()).isTrue();
        verify(assetStore).findWorkspacePage(workspaceId, null, 3, 20);
    }

    @Test
    void detailPreservesYoutubeSourceIdentityWithoutUploadFields() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = Asset.youtube(
                assetId, "video-id", "YouTube lecture", AssetStatus.PROCESSING, workspaceId
        );
        when(assetStore.findById(assetId)).thenReturn(Optional.of(asset));
        when(workspaceAccess.isOwnedByCurrentUser(workspaceId)).thenReturn(true);

        var result = service.getAsset(assetId);

        assertThat(result.sourceType()).isEqualTo(AssetSourceType.YOUTUBE);
        assertThat(result.youtubeVideoId()).isEqualTo("video-id");
        assertThat(result.originalFilename()).isNull();
        assertThat(result.contentType()).isNull();
        assertThat(result.sizeBytes()).isNull();
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
        Asset asset = Asset.uploaded(
                id, "lecture.mp4", "Lecture", status, workspaceId,
                "workspace-media", "objects/lecture.mp4", "video/mp4", 42L, null
        );
        ReflectionTestUtils.setField(asset, "createdAt", createdAt);
        ReflectionTestUtils.setField(asset, "updatedAt", createdAt);
        return asset;
    }
}
