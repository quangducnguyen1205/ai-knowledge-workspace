package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.assistant;

import java.util.List;

record FastApiAssistantAnswerResponse(
        String answer,
        List<String> citedSourceIds,
        Boolean insufficientContext
) {

    FastApiAssistantAnswerResponse {
        if (citedSourceIds != null) {
            citedSourceIds = List.copyOf(citedSourceIds);
        }
    }
}
