package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import java.util.UUID;

public record AssistantAnswerRequest(
        UUID workspaceId,
        String question,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
