package com.aiknowledgeworkspace.workspacecore.identity.application.port.out;

public class UserAccountConflictException extends RuntimeException {

    public UserAccountConflictException(Throwable cause) {
        super("User account persistence conflict", cause);
    }
}
