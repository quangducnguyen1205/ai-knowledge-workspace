package com.aiknowledgeworkspace.workspacecore.outbox.application.port.out;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;

public interface OutboxMessagePublisher {

    void publish(OutboxEvent event);
}
