package com.aiknowledgeworkspace.workspacecore.outbox.application;

import java.util.UUID;

public interface OutboxWriter {
    UUID enqueue(OutboxDraft draft);
}
