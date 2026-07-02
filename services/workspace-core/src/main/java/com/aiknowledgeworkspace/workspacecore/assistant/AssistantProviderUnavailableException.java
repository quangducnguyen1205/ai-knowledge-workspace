package com.aiknowledgeworkspace.workspacecore.assistant;

public class AssistantProviderUnavailableException extends RuntimeException {

    public AssistantProviderUnavailableException(String message) {
        super(message);
    }

    public AssistantProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
