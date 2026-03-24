package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

public class FastApiTimeoutException extends FastApiIntegrationException {

    public FastApiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
