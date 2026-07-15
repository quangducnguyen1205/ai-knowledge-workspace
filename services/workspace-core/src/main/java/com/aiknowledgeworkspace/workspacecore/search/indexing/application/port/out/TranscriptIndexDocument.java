package com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out;

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
