package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.ProcessingResultAssetPortAdapter;
import com.aiknowledgeworkspace.workspacecore.asset.application.transcript.AssetTranscriptSnapshotService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.processing.application.artifact.ProcessingTranscriptRow;
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
class ProcessingResultAssetPortAdapterTest {

    @Mock
    private AssetTranscriptSnapshotService transcriptSnapshotService;

    @Test
    void successfulResultUsesCanonicalSnapshotThenLifecycleTransition() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId);
        when(transcriptSnapshotService.loadAsset(assetId)).thenReturn(asset);
        ProcessingResultAssetPortAdapter adapter = new ProcessingResultAssetPortAdapter(transcriptSnapshotService);

        adapter.applyTranscriptReady(assetId, List.of(new ProcessingTranscriptRow(
                "row-1", "video-1", 0, "canonical", "2026-06-26T00:00:00Z"
        )));

        ArgumentCaptor<List<AssetTranscriptRowInput>> rows = ArgumentCaptor.forClass(List.class);
        var ordered = inOrder(transcriptSnapshotService);
        ordered.verify(transcriptSnapshotService).applySuccessfulProcessingResult(
                org.mockito.Mockito.eq(asset),
                rows.capture()
        );
        org.assertj.core.api.Assertions.assertThat(rows.getValue()).containsExactly(new AssetTranscriptRowInput(
                "row-1", "video-1", 0, "canonical", "2026-06-26T00:00:00Z"
        ));
    }

    @Test
    void failedResultUsesCanonicalAssetLifecycleOwner() {
        UUID assetId = UUID.randomUUID();

        new ProcessingResultAssetPortAdapter(transcriptSnapshotService).applyProcessingFailed(assetId);

        verify(transcriptSnapshotService).markProcessingFailed(assetId);
    }

    private Asset asset(UUID assetId) {
        Asset asset = new Asset(
                "lecture.mp4", "Lecture", AssetStatus.PROCESSING, UUID.randomUUID()
        );
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }
}
