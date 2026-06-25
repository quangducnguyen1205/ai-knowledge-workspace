package com.aiknowledgeworkspace.workspacecore.assistant;

import java.util.List;
import java.util.UUID;

public record AssistantContextResponse(
        UUID workspaceId,
        String query,
        List<AssistantContextSourceResponse> sources
) {
}
