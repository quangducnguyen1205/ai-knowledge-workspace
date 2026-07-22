package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import java.util.UUID;

public record AssistantCitationResponse(
        UUID assetId,
        String transcriptRowId,
        Integer segmentIndex,
        Long startMs,
        Long endMs
) {
}
