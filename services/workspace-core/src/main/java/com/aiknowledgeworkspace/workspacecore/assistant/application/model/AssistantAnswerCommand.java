package com.aiknowledgeworkspace.workspacecore.assistant.application.model;

import java.util.UUID;

public record AssistantAnswerCommand(
        UUID workspaceId,
        String question,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
