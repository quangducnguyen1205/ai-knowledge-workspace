package com.aiknowledgeworkspace.workspacecore.search.application.exception;

public class InvalidSearchRequestException extends RuntimeException {

    private final String code;

    public InvalidSearchRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
