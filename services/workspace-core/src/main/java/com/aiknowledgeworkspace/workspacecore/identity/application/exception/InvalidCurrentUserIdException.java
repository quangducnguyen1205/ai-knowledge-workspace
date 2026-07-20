package com.aiknowledgeworkspace.workspacecore.identity.application.exception;

public class InvalidCurrentUserIdException extends RuntimeException {

    public InvalidCurrentUserIdException(String message) {
        super(message);
    }
}
