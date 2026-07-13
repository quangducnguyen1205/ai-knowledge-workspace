package com.aiknowledgeworkspace.workspacecore.search;

public class SearchTranscriptUnavailableException extends RuntimeException {
    private final String code;

    public SearchTranscriptUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
