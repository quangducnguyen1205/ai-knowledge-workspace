package com.aiknowledgeworkspace.workspacecore.search.adapter.in.web;

import java.util.UUID;

public record SearchResultResponse(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String text,
        String createdAt,
        Double score
) {
}
