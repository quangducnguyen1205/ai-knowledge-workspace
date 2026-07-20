package com.aiknowledgeworkspace.workspacecore.assistant.application.port.out;

public interface AssistantAnswerProviderPort {

    AssistantProviderResponse answer(AssistantProviderRequest request);
}
