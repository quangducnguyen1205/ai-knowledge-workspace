package com.aiknowledgeworkspace.workspacecore.asset;

public class AssetListRequestException extends RuntimeException {

    private final String code;

    public AssetListRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
