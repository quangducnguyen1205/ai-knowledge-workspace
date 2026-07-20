package com.aiknowledgeworkspace.workspacecore.assistant.application.port.out;

import java.util.UUID;

public record AssistantSearchHit(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String text,
        String createdAt,
        Double score
) {
}
