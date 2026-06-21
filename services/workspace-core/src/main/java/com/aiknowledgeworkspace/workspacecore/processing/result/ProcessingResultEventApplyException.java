package com.aiknowledgeworkspace.workspacecore.processing.result;

class ProcessingResultEventApplyException extends RuntimeException {

    ProcessingResultEventApplyException(String message) {
        super(message);
    }

    ProcessingResultEventApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
