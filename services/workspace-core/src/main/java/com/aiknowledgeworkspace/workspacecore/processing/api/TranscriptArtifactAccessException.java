package com.aiknowledgeworkspace.workspacecore.processing.api;

public class TranscriptArtifactAccessException extends RuntimeException {

    public TranscriptArtifactAccessException(String message) {
        super(message);
    }

    public TranscriptArtifactAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
