package com.aiknowledgeworkspace.workspacecore.search;

public class ElasticsearchIntegrationException extends RuntimeException {

    public ElasticsearchIntegrationException(String message) {
        super(message);
    }

    public ElasticsearchIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
