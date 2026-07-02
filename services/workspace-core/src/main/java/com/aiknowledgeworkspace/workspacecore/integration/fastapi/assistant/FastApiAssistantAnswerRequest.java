package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant;

import java.util.List;

public record FastApiAssistantAnswerRequest(
        String question,
        List<FastApiAssistantSourceRequest> sources
) {
    public FastApiAssistantAnswerRequest {
        sources = List.copyOf(sources);
    }
}
