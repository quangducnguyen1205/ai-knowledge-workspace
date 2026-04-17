package com.aiknowledgeworkspace.workspacecore.workspace;

public class WorkspaceDeleteConflictException extends RuntimeException {

    private final String code;

    public WorkspaceDeleteConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
