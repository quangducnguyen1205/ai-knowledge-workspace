package com.aiknowledgeworkspace.workspacecore.assistant.adapter.in.web;

import java.util.List;

public record AssistantAnswerResponse(
        String answer,
        List<AssistantAnswerCitationResponse> citations,
        boolean insufficientContext
) {
    public AssistantAnswerResponse {
        citations = List.copyOf(citations);
    }
}
