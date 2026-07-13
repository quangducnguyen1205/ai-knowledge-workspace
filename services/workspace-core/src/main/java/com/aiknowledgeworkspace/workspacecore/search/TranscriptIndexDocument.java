package com.aiknowledgeworkspace.workspacecore.search;

import java.util.UUID;

public record TranscriptIndexDocument(
        UUID assetId,
        UUID workspaceId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String text,
        String createdAt,
        String assetStatus
) {
}
