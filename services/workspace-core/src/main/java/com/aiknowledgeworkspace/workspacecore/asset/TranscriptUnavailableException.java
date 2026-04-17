package com.aiknowledgeworkspace.workspacecore.asset;

public class TranscriptUnavailableException extends RuntimeException {

    private final String code;

    public TranscriptUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
