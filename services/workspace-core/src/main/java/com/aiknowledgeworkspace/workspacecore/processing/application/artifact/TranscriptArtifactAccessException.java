package com.aiknowledgeworkspace.workspacecore.processing.application.artifact;

public class TranscriptArtifactAccessException extends RuntimeException {

    public TranscriptArtifactAccessException(String message) {
        super(message);
    }

    public TranscriptArtifactAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
