package com.aiknowledgeworkspace.workspacecore.search.application.service;

public class AssetIndexingEventRejectedException extends RuntimeException {

    public AssetIndexingEventRejectedException(String message) {
        super(message);
    }

    public AssetIndexingEventRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
