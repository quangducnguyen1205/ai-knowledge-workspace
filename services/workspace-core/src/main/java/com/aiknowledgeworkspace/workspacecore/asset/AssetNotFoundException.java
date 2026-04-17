package com.aiknowledgeworkspace.workspacecore.asset;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException() {
        super("Asset not found");
    }
}
