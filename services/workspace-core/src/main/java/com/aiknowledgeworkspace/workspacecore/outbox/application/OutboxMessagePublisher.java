package com.aiknowledgeworkspace.workspacecore.outbox.application;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxEvent;

public interface OutboxMessagePublisher {

    void publish(OutboxEvent event);
}
