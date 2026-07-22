package com.aiknowledgeworkspace.workspacecore.assistant.application.result;

import java.util.UUID;

public record AssistantContextSource(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String createdAt,
        String text,
        AssistantCitation citation
) {
}
