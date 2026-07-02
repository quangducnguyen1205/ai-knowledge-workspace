package com.aiknowledgeworkspace.workspacecore.assistant;

import java.util.UUID;

public record AssistantAnswerRequest(
        UUID workspaceId,
        String question,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
