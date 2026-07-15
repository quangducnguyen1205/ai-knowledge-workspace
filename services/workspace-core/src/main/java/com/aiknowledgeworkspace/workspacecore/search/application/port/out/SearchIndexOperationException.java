package com.aiknowledgeworkspace.workspacecore.search.application.port.out;

public class SearchIndexOperationException extends RuntimeException {

    public SearchIndexOperationException(String message) {
        super(message);
    }

    public SearchIndexOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
