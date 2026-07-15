package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.internal;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantAnswerProviderPort;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderRequest;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderResponse;
import com.aiknowledgeworkspace.workspacecore.assistant.application.port.AssistantProviderSource;
import org.springframework.stereotype.Component;

@Component
class FastApiAssistantProviderAdapter implements AssistantAnswerProviderPort {

    private final FastApiAssistantClient client;

    FastApiAssistantProviderAdapter(FastApiAssistantClient client) {
        this.client = client;
    }

    @Override
    public AssistantProviderResponse answer(AssistantProviderRequest request) {
        FastApiAssistantAnswerResponse response = client.answer(new FastApiAssistantAnswerRequest(
                request.question(),
                request.sources().stream().map(this::toWireSource).toList()
        ));
        return new AssistantProviderResponse(
                response.answer(),
                response.citedSourceIds(),
                response.insufficientContext()
        );
    }

    private FastApiAssistantSourceRequest toWireSource(AssistantProviderSource source) {
        return new FastApiAssistantSourceRequest(
                source.sourceId(),
                source.assetId(),
                source.assetTitle(),
                source.transcriptRowId(),
                source.segmentIndex(),
                source.createdAt(),
                source.text()
        );
    }
}
