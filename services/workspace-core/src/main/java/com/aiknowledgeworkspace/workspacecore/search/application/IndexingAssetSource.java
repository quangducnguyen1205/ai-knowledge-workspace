package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.List;
import java.util.UUID;

public record IndexingAssetSource(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        List<IndexingTranscriptRow> transcriptRows
) {
    public IndexingAssetSource {
        transcriptRows = List.copyOf(transcriptRows);
    }
}
