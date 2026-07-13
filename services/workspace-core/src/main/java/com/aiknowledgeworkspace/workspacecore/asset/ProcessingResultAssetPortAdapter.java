package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingResultAssetPort;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingTranscriptRow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProcessingResultAssetPortAdapter implements ProcessingResultAssetPort {

    private final AssetTranscriptSnapshotService transcriptSnapshotService;

    ProcessingResultAssetPortAdapter(AssetTranscriptSnapshotService transcriptSnapshotService) {
        this.transcriptSnapshotService = transcriptSnapshotService;
    }

    @Override
    public void applyTranscriptReady(UUID assetId, List<ProcessingTranscriptRow> transcriptRows) {
        try {
            Asset asset = transcriptSnapshotService.loadAsset(assetId);
            transcriptSnapshotService.applySuccessfulProcessingResult(asset, transcriptRows.stream()
                    .map(row -> new AssetTranscriptRowInput(
                            row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                    ))
                    .toList());
        } catch (AssetNotFoundException exception) {
            throw new ProcessingAssetUnavailableException(exception);
        }
    }

    @Override
    public void applyProcessingFailed(UUID assetId) {
        try {
            transcriptSnapshotService.markProcessingFailed(assetId);
        } catch (AssetNotFoundException exception) {
            throw new ProcessingAssetUnavailableException(exception);
        }
    }
}
