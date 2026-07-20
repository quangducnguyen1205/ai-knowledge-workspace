package com.aiknowledgeworkspace.workspacecore.assistant.application.model;

import java.util.UUID;

public record AssistantAnswerCitation(
        String sourceId,
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt
) {
}
