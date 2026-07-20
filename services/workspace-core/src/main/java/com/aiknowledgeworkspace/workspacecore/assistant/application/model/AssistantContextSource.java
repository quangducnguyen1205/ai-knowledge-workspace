package com.aiknowledgeworkspace.workspacecore.assistant.application.model;

import java.util.UUID;

public record AssistantContextSource(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt,
        String text,
        AssistantCitation citation
) {
}
