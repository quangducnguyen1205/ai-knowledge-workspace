package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import java.util.List;
import java.util.UUID;

public record AssetTranscriptContextResponse(
        UUID assetId,
        String transcriptRowId,
        Integer hitSegmentIndex,
        int window,
        List<AssetTranscriptRowResponse> rows
) {
}
