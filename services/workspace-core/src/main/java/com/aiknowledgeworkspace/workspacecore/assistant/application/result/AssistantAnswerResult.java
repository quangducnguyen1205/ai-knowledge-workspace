package com.aiknowledgeworkspace.workspacecore.assistant.application.result;

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
