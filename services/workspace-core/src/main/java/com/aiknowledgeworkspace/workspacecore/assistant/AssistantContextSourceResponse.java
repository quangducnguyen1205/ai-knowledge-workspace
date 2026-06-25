package com.aiknowledgeworkspace.workspacecore.assistant;

import java.util.UUID;

public record AssistantContextSourceResponse(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt,
        String text,
        AssistantCitationResponse citation
) {
}
