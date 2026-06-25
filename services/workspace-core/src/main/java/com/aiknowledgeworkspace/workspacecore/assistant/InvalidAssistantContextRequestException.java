package com.aiknowledgeworkspace.workspacecore.assistant;

public class InvalidAssistantContextRequestException extends RuntimeException {

    private final String code;

    public InvalidAssistantContextRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
