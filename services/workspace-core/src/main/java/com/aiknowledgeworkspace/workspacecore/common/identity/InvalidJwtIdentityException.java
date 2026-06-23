package com.aiknowledgeworkspace.workspacecore.common.identity;

public class InvalidJwtIdentityException extends RuntimeException {

    public InvalidJwtIdentityException(String message) {
        super(message);
    }
}
