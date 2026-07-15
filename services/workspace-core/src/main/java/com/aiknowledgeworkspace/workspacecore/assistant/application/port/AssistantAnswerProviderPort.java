package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

public interface AssistantAnswerProviderPort {

    AssistantProviderResponse answer(AssistantProviderRequest request);
}
