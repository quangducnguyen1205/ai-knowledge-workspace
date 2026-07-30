package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class CanonicalTranscriptContextReadException extends RuntimeException {

    public CanonicalTranscriptContextReadException() {
        super("Canonical transcript context could not be read");
    }
}
