package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import java.util.UUID;

public record FastApiAssistantSourceRequest(
        String sourceId,
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt,
        String text
) {
}
