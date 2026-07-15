package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.internal;

import java.util.List;

record FastApiAssistantAnswerRequest(String question, List<FastApiAssistantSourceRequest> sources) {

    FastApiAssistantAnswerRequest {
        sources = List.copyOf(sources);
    }
}
