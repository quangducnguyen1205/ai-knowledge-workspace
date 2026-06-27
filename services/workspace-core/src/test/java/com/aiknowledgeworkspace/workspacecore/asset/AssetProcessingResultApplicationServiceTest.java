package com.aiknowledgeworkspace.workspacecore.asset;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class AssetProcessingResultApplicationServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    @Test
    void transcriptReadyReplacesCanonicalTranscriptAndMarksNonSearchableAssetReady() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        List<AssetTranscriptRowInput> rows = List.of(new AssetTranscriptRowInput(
                "row-1",
                "video-1",
                0,
                "canonical",
                "2026-06-26T00:00:00Z"
        ));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        service().applyTranscriptReady(assetId, rows);

        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, rows);
        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void transcriptReadyDoesNotDowngradeAlreadySearchableAsset() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.SEARCHABLE);
        List<AssetTranscriptRowInput> rows = List.of(new AssetTranscriptRowInput(
                "row-1",
                "video-1",
                0,
                "canonical",
                "2026-06-26T00:00:00Z"
        ));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        service().applyTranscriptReady(assetId, rows);

        verify(assetPersistenceService).replaceTranscriptSnapshot(asset, rows);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
    }

    @Test
    void processingFailedMarksAssetFailedThroughAssetLifecycleRule() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        service().applyProcessingFailed(assetId);

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.FAILED);
    }

    private AssetProcessingResultApplicationService service() {
        return new AssetProcessingResultApplicationService(assetRepository, assetPersistenceService);
    }

    private Asset asset(UUID assetId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, new Workspace(UUID.randomUUID(), "Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }
}
