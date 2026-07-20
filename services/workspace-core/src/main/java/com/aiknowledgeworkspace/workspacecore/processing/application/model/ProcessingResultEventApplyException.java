package com.aiknowledgeworkspace.workspacecore.processing.application.model;

public class ProcessingResultEventApplyException extends RuntimeException {

    public ProcessingResultEventApplyException(String message) {
        super(message);
    }

    public ProcessingResultEventApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
