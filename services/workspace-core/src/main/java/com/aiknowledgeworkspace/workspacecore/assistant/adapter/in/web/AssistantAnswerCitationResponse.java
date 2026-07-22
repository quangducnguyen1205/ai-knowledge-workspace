package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import java.util.UUID;

public record AssistantAnswerCitationResponse(
        String sourceId,
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String createdAt
) {
}
