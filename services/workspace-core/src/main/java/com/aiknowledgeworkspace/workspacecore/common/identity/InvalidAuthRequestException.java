package com.aiknowledgeworkspace.workspacecore.common.identity;

public class InvalidAuthRequestException extends RuntimeException {

    private final String code;

    public InvalidAuthRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
