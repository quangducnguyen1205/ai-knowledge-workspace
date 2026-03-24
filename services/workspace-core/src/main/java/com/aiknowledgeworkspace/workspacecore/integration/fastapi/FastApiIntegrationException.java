package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

public class FastApiIntegrationException extends RuntimeException {

    public FastApiIntegrationException(String message) {
        super(message);
    }

    public FastApiIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
