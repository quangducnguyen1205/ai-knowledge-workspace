package com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch;

public class ElasticsearchConnectivityException extends ElasticsearchIntegrationException {

    public ElasticsearchConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }
}
