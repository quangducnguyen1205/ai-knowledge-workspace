package com.aiknowledgeworkspace.workspacecore.search.application.exception;

public class SearchAssetNotFoundException extends RuntimeException {
    public SearchAssetNotFoundException() {
        super("Asset not found");
    }
}
