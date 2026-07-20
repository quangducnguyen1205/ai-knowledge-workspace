package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException() {
        super("Asset not found");
    }
}
