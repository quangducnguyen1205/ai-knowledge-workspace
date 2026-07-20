package com.aiknowledgeworkspace.workspacecore.assistant.application.query;

import java.util.UUID;

public record AssistantContextQuery(
        UUID workspaceId,
        String query,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
