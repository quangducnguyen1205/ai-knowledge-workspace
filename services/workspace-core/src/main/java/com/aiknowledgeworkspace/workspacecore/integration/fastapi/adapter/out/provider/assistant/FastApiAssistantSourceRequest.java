package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.assistant;

import java.util.UUID;

record FastApiAssistantSourceRequest(
        String sourceId,
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt,
        String text
) {
}
