package com.aiknowledgeworkspace.workspacecore.search;

public class SearchAssetNotFoundException extends RuntimeException {
    public SearchAssetNotFoundException() {
        super("Asset not found");
    }
}
