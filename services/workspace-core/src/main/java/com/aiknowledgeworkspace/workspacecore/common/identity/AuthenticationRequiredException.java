package com.aiknowledgeworkspace.workspacecore.common.identity;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
