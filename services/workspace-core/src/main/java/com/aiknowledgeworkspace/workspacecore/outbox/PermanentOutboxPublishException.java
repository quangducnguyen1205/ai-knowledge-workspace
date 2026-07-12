package com.aiknowledgeworkspace.workspacecore.outbox;

public class PermanentOutboxPublishException extends OutboxPublishException {

    public PermanentOutboxPublishException(String message) {
        super(message);
    }

    public PermanentOutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
