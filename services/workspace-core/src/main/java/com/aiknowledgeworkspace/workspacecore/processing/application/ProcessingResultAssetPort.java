package com.aiknowledgeworkspace.workspacecore.processing.application;

import java.util.List;
import java.util.UUID;

public interface ProcessingResultAssetPort {
    void applyTranscriptReady(UUID assetId, List<ProcessingTranscriptRow> transcriptRows);

    void applyProcessingFailed(UUID assetId);
}
