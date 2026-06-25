package com.aiknowledgeworkspace.workspacecore.assistant;

import java.util.UUID;

public record AssistantCitationResponse(
        UUID assetId,
        String transcriptRowId,
        Integer segmentIndex
) {
}
