package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant;

public interface FastApiAssistantClient {

    FastApiAssistantAnswerResponse answer(FastApiAssistantAnswerRequest request);
}
