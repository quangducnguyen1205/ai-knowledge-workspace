package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.assistant;

import java.util.List;

record FastApiAssistantAnswerRequest(String question, List<FastApiAssistantSourceRequest> sources) {

    FastApiAssistantAnswerRequest {
        sources = List.copyOf(sources);
    }
}
