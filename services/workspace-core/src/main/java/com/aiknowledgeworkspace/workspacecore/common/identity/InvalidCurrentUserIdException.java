package com.aiknowledgeworkspace.workspacecore.common.identity;

public class InvalidCurrentUserIdException extends RuntimeException {

    public InvalidCurrentUserIdException(String message) {
        super(message);
    }
}
