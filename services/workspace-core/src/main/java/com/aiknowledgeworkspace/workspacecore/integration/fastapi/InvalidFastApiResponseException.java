package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

public class InvalidFastApiResponseException extends FastApiIntegrationException {

    public InvalidFastApiResponseException(String message) {
        super(message);
    }
}
