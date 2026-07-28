package com.aiknowledgeworkspace.workspacecore.storage.api;

public class StoredObjectNotFoundException extends RuntimeException {

    public StoredObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
