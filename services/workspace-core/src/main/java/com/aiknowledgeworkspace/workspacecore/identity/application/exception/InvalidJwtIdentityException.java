package com.aiknowledgeworkspace.workspacecore.identity.application.exception;

public class InvalidJwtIdentityException extends RuntimeException {

    public InvalidJwtIdentityException(String message) {
        super(message);
    }
}
