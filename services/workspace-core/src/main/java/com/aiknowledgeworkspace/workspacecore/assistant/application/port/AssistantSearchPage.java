package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

import java.util.List;
import java.util.UUID;

public record AssistantSearchPage(
        UUID workspaceIdFilter,
        List<AssistantSearchHit> results
) {
    public AssistantSearchPage {
        results = List.copyOf(results);
    }
}
