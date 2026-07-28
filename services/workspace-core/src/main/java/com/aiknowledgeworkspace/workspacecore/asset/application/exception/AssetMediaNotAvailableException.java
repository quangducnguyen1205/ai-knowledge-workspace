package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class AssetMediaNotAvailableException extends RuntimeException {

    public AssetMediaNotAvailableException() {
        super("Asset media is not available");
    }

    public AssetMediaNotAvailableException(Throwable cause) {
        super("Asset media is not available", cause);
    }
}
