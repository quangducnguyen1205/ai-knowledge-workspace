package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

public interface FastApiAssistantClient {

    FastApiAssistantAnswerResponse answer(FastApiAssistantAnswerRequest request);
}
