package com.aiknowledgeworkspace.workspacecore.search.application.query;

import java.util.UUID;

public record SearchHit(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String text,
        String createdAt,
        Double score
) {
}
