package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptSnapshotService;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingResultAssetPort;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingTranscriptRow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessingResultAssetPortAdapter implements ProcessingResultAssetPort {

    private final AssetTranscriptSnapshotService transcriptSnapshotService;

    public ProcessingResultAssetPortAdapter(AssetTranscriptSnapshotService transcriptSnapshotService) {
        this.transcriptSnapshotService = transcriptSnapshotService;
    }

    @Override
    public void applyTranscriptReady(UUID assetId, List<ProcessingTranscriptRow> transcriptRows) {
        try {
            Asset asset = transcriptSnapshotService.loadAsset(assetId);
            transcriptSnapshotService.applySuccessfulProcessingResult(asset, transcriptRows.stream()
                    .map(row -> new AssetTranscriptRowInput(
                            row.id(), row.videoId(), row.segmentIndex(), row.startMs(), row.endMs(),
                            row.text(), row.createdAt()
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
