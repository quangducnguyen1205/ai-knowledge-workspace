package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common;

public class InvalidFastApiResponseException extends FastApiIntegrationException {

    public InvalidFastApiResponseException(String message) {
        super(message);
    }
}
