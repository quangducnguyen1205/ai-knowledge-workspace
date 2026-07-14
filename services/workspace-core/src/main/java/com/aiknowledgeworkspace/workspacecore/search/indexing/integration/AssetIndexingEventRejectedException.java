package com.aiknowledgeworkspace.workspacecore.search.indexing.integration;

public class AssetIndexingEventRejectedException extends RuntimeException {

    public AssetIndexingEventRejectedException(String message) {
        super(message);
    }

    public AssetIndexingEventRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
