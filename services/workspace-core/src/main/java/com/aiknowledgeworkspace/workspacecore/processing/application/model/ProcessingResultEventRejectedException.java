package com.aiknowledgeworkspace.workspacecore.processing.application.model;

public class ProcessingResultEventRejectedException extends RuntimeException {

    public ProcessingResultEventRejectedException(String message) {
        super(message);
    }

    public ProcessingResultEventRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
