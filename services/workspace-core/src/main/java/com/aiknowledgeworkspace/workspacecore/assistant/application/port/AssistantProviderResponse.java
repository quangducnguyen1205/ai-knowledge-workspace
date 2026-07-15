package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

import java.util.List;

public record AssistantProviderResponse(
        String answer,
        List<String> citedSourceIds,
        Boolean insufficientContext
) {

    public AssistantProviderResponse {
        if (citedSourceIds != null) {
            citedSourceIds = List.copyOf(citedSourceIds);
        }
    }
}
