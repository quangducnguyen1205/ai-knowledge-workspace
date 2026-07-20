package com.aiknowledgeworkspace.workspacecore.assistant.application.result;

import java.util.List;
import java.util.UUID;

public record AssistantContextResult(UUID workspaceId, String query, List<AssistantContextSource> sources) {
    public AssistantContextResult {
        sources = List.copyOf(sources);
    }
}
