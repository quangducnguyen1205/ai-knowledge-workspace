package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class AssetMediaReadException extends RuntimeException {

    public AssetMediaReadException() {
        super("Asset media could not be read");
    }
}
