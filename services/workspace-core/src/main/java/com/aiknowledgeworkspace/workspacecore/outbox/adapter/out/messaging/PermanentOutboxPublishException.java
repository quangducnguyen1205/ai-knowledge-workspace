package com.aiknowledgeworkspace.workspacecore.outbox.adapter.out.messaging;

public class PermanentOutboxPublishException extends OutboxPublishException {

    public PermanentOutboxPublishException(String message) {
        super(message);
    }

    public PermanentOutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
