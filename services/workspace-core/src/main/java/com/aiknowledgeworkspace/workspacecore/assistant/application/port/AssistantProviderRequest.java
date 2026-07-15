package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

import java.util.List;

public record AssistantProviderRequest(String question, List<AssistantProviderSource> sources) {

    public AssistantProviderRequest {
        sources = List.copyOf(sources);
    }
}
