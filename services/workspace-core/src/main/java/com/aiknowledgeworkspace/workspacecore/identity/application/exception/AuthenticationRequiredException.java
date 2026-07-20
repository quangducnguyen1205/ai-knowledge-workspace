package com.aiknowledgeworkspace.workspacecore.identity.application.exception;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
