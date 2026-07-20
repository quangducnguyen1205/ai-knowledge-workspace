package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import java.util.List;
import java.util.UUID;

public record AssetTranscriptContext(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer hitSegmentIndex,
        int window,
        List<AssetTranscriptRowView> rows
) {

    public AssetTranscriptContext {
        rows = List.copyOf(rows);
    }
}
