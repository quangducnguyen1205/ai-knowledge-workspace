package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import java.util.UUID;

public record AssistantContextRequest(
        UUID workspaceId,
        String query,
        UUID assetId,
        Integer maxSources,
        Integer contextWindow
) {
}
