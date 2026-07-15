package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.internal;

interface FastApiAssistantClient {

    FastApiAssistantAnswerResponse answer(FastApiAssistantAnswerRequest request);
}
