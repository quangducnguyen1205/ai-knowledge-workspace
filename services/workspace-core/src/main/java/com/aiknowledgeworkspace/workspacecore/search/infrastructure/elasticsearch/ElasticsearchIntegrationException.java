package com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch;

public class ElasticsearchIntegrationException extends RuntimeException {

    public ElasticsearchIntegrationException(String message) {
        super(message);
    }

    public ElasticsearchIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
