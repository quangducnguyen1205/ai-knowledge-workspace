package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import java.util.List;

public record FastApiAssistantAnswerResponse(
        String answer,
        List<String> citedSourceIds,
        Boolean insufficientContext
) {
    public FastApiAssistantAnswerResponse {
        if (citedSourceIds != null) {
            citedSourceIds = List.copyOf(citedSourceIds);
        }
    }
}
