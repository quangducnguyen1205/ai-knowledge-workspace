package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.assistant;

interface FastApiAssistantClient {

    FastApiAssistantAnswerResponse answer(FastApiAssistantAnswerRequest request);
}
