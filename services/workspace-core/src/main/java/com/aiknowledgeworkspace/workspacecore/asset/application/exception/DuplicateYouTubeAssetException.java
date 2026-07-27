package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class DuplicateYouTubeAssetException extends RuntimeException {

    public DuplicateYouTubeAssetException() {
        super("This YouTube video already exists in the workspace");
    }

    public DuplicateYouTubeAssetException(Throwable cause) {
        super("This YouTube video already exists in the workspace", cause);
    }
}
