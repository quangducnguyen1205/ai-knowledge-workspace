package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import java.util.List;
import java.util.UUID;

public record AssetIndexingSource(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        List<AssetTranscriptRowView> transcriptRows
) {

    public AssetIndexingSource {
        transcriptRows = List.copyOf(transcriptRows);
    }
}
