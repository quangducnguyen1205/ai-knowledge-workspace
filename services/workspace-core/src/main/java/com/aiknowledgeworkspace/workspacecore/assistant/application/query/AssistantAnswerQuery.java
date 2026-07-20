package com.aiknowledgeworkspace.workspacecore.assistant.application.query;

import java.util.UUID;

public record AssistantAnswerQuery(
        UUID workspaceId,
        String question,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
