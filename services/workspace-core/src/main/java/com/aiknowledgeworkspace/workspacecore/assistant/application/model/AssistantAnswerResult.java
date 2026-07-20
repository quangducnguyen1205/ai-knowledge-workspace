package com.aiknowledgeworkspace.workspacecore.assistant.application.model;

import java.util.List;

public record AssistantAnswerResult(
        String answer,
        List<AssistantAnswerCitation> citations,
        boolean insufficientContext
) {
    public AssistantAnswerResult {
        citations = List.copyOf(citations);
    }
}
