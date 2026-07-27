package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class AssetProcessingRetryNotAllowedException extends RuntimeException {

    public AssetProcessingRetryNotAllowedException() {
        super("Processing can only be retried for a failed asset");
    }
}
